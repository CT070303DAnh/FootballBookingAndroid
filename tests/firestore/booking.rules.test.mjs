import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { after, before, beforeEach, test } from 'node:test';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, query, where, setDoc, updateDoc, deleteDoc, writeBatch, runTransaction, serverTimestamp } from 'firebase/firestore';

if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Use npm test; emulator required.');
let env;
const dbFor = uid => env.authenticatedContext(uid).firestore();
const dayPath = 'pitches/pitch/availability/2099-01-01';
const booking = (id = 'b1', customerId = 'customer', extra = {}) => ({
  bookingId: id, customerId, customerName: 'Customer', customerPhone: '0900000000',
  pitchId: 'pitch', pitchOwnerId: 'owner', pitchName: 'Pitch', pitchAddress: 'Address', pitchType: '5',
  bookingDate: '2099-01-01', slotId: 'slot_1', startTime: '06:00', endTime: '07:30',
  services: [], basePrice: 200000, surcharge: 0, totalServicePrice: 0, totalAmount: 200000,
  paymentStatus: 'unpaid', paymentMethod: 'cash', bookingStatus: 'pending', matchStatus: 'upcoming',
  note: '', createdAt: serverTimestamp(), updatedAt: serverTimestamp(), ...extra,
});
const day = (id = 'b1', status = 'pending', slotId = 'slot_1') => ({
  date: '2099-01-01', lastSlotId: slotId, slots: { [slotId]: { status, bookingId: id } },
});
const seed = async (path, data) => env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), path), data));
function create(db, b = booking(), a = day(b.bookingId, 'pending', b.slotId)) {
  const batch = writeBatch(db);
  batch.set(doc(db, 'bookings', b.bookingId), b);
  batch.set(doc(db, `pitches/${b.pitchId}/availability/${b.bookingDate}`), a, { merge: true });
  return batch.commit();
}
function transition(db, id, status, extra = {}, slotId = 'slot_1') {
  const batch = writeBatch(db);
  batch.update(doc(db, 'bookings', id), { bookingStatus: status, updatedAt: serverTimestamp(), ...extra });
  batch.set(doc(db, dayPath), day(status === 'approved' ? id : null, status === 'approved' ? 'booked' : 'available', slotId), { merge: true });
  return batch.commit();
}
before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-football-booking', firestore: {
    rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
  } });
});
beforeEach(async () => {
  await env.clearFirestore();
  for (const [uid, role, status] of [['customer','customer','active'], ['other','customer','active'],
    ['owner','owner','active'], ['stranger','owner','active'], ['admin','admin','active'],
    ['blocked','customer','blocked'], ['pending','owner_pending','active']]) {
    await seed(`users/${uid}`, { uid, role, status });
  }
  await seed('pitches/pitch', { ownerId: 'owner', name: 'Pitch', address: 'Address', type: '5', basePrice: 200000, status: 'available', bookingPolicyVersion: 2, useDefaultSlots: true });
});
after(async () => { if (env) await env.cleanup(); });

test('private bookings readable only by customer, owning owner and admin', async () => {
  await seed('bookings/b1', booking());
  for (const uid of ['customer', 'owner', 'admin']) await assertSucceeds(getDoc(doc(dbFor(uid), 'bookings/b1')));
  for (const uid of ['other', 'stranger', 'blocked', 'pending']) await assertFails(getDoc(doc(dbFor(uid), 'bookings/b1')));
  await assertFails(getDocs(collection(dbFor('customer'), 'bookings')));
  await assertSucceeds(getDocs(query(collection(dbFor('customer'), 'bookings'), where('customerId', '==', 'customer'))));
  await assertSucceeds(getDocs(query(collection(dbFor('owner'), 'bookings'), where('pitchOwnerId', '==', 'owner'))));
});

test('profiles are private while availability remains readable by active users', async () => {
  await seed(dayPath, day());
  await assertSucceeds(getDoc(doc(dbFor('customer'), 'users/customer')));
  await assertSucceeds(getDoc(doc(dbFor('admin'), 'users/customer')));
  await assertFails(getDoc(doc(dbFor('other'), 'users/customer')));
  await assertFails(getDocs(collection(dbFor('customer'), 'users')));
  await assertSucceeds(getDoc(doc(dbFor('other'), dayPath)));
  await assertFails(getDoc(doc(dbFor('blocked'), dayPath)));
});

test('blocked owners cannot edit services or create reviews', async () => {
  await seed('users/owner', {uid:'owner', role:'owner', status:'blocked'});
  await assertFails(setDoc(doc(dbFor('owner'), 'services/test'), {name:'test', price:1, isActive:true}));
  await assertFails(setDoc(doc(dbFor('owner'), 'reviews/test'), {customerId:'owner'}));
});

test('valid booking and availability commit together', async () => {
  await assertSucceeds(create(dbFor('customer')));
});

for (const method of ['cash', 'vnpay', 'pay_later']) {
  test(`booking with ${method} starts unpaid and locks the slot`, async () => {
    await assertSucceeds(create(dbFor('customer'), booking('b1', 'customer', {paymentMethod: method})));
    const saved = await getDoc(doc(dbFor('customer'), 'bookings/b1'));
    assert.equal(saved.data().paymentMethod, method);
    assert.equal(saved.data().paymentStatus, 'unpaid');
    const availability = await getDoc(doc(dbFor('customer'), dayPath));
    assert.equal(availability.data().slots.slot_1.bookingId, 'b1');
  });
}
test('no client role can mark a booking paid or forge server payment fields', async () => {
  await create(dbFor('customer'));
  for (const uid of ['customer','owner','admin']) {
    for (const update of [{paymentStatus:'paid'}, {vnpayTransactionId:'123'}, {paymentSessionId:'fake'}, {paymentReviewRequired:false}]) {
      await assertFails(updateDoc(doc(dbFor(uid),'bookings/b1'),update));
    }
  }
});
test('payment session and bank transaction documents are inaccessible to clients', async () => {
  for (const path of ['payment_sessions/ref','vnpay_transactions/123']) {
    await seed(path,{bookingId:'b1'});
    for (const uid of ['customer','owner','admin']) {
      const ref=doc(dbFor(uid),path);
      await assertFails(getDoc(ref));
      await assertFails(setDoc(ref,{status:'paid'}));
      await assertFails(deleteDoc(ref));
    }
    await assertFails(setDoc(doc(dbFor('customer'),path+'-new'),{status:'paid'}));
  }
});
test('verified payment fields survive cancellation and cannot be rewritten alongside it', async () => {
  await create(dbFor('customer'));
  await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(),'bookings/b1'),{
    paymentStatus:'paid',paymentSessionId:'server-ref',vnpayTransactionId:'123',paymentReviewRequired:false,
  }));
  await assertFails(transition(dbFor('customer'),'b1','cancelled',{paymentStatus:'refunded'}));
  await assertFails(transition(dbFor('customer'),'b1','cancelled',{paymentSessionId:'other'}));
  await assertSucceeds(transition(dbFor('customer'),'b1','cancelled'));
  assert.equal((await getDoc(doc(dbFor('customer'),'bookings/b1'))).data().vnpayTransactionId,'123');
});
test('booking alone and orphan availability are rejected', async () => {
  const db = dbFor('customer');
  await assertFails(setDoc(doc(db, 'bookings/b1'), booking()));
  await assertFails(setDoc(doc(db, dayPath), day()));
  await assertFails(setDoc(doc(db, dayPath), day(null, 'available')));
});
for (const [label, extra] of [
  ['wrong owner', { pitchOwnerId: 'customer' }], ['wrong customer', { customerId: 'other' }],
  ['wrong id', { bookingId: 'wrong' }], ['discounted base', { basePrice: 1, totalAmount: 1 }],
  ['discounted total', { totalAmount: 1 }], ['fake surcharge', { surcharge: -10, totalAmount: 199990 }],
  ['fake slot', { slotId: 'fake' }], ['changed time', { startTime: '06:01' }],
  ['invalid date', { bookingDate: '2099-02-30' }], ['past date', { bookingDate: '2020-01-01' }],
  ['paid on creation', { paymentStatus: 'paid' }], ['approved on creation', { bookingStatus: 'approved' }],
  ['injected field', { refundAmount: 200000 }], ['fake transaction', { vnpayTransactionId: 'fake' }],
]) {
  test(`reject ${label}`, async () => {
    const b = booking('b1', 'customer', extra);
    const db = dbFor('customer');
    const batch = writeBatch(db);
    batch.set(doc(db, 'bookings/b1'), b);
    batch.set(doc(db, `pitches/${b.pitchId}/availability/${b.bookingDate}`), { ...day('b1', 'pending', b.slotId), date: b.bookingDate });
    await assertFails(batch.commit());
  });
}
test('blocked, pending owner, owner and anonymous cannot create bookings', async () => {
  for (const uid of ['blocked', 'pending', 'owner']) await assertFails(create(dbFor(uid), booking('b1', uid)));
  await assertFails(create(env.unauthenticatedContext().firestore()));
});
test('closed pitch and disabled custom slot cannot be booked', async () => {
  await seed('pitches/pitch/timeSlots/slot_1', { startTime: '06:00', endTime: '07:30', isActive: false, isPeakHour: false, surcharge: 0 });
  await assertFails(create(dbFor('customer')));
  await env.withSecurityRulesDisabled(c => deleteDoc(doc(c.firestore(), 'pitches/pitch/timeSlots/slot_1')));
  await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(), 'pitches/pitch'), { status: 'closed' }));
  await assertFails(create(dbFor('customer')));
});
test('configured slot and peak surcharge are checked against catalog', async () => {
  await seed('pitches/pitch/timeSlots/custom', { startTime: '10:30', endTime: '12:00', isActive: true, isPeakHour: true, surcharge: 12000 });
  await assertSucceeds(create(dbFor('customer'), booking('b1','customer', { slotId: 'custom', startTime: '10:30', endTime: '12:00', surcharge: 12000, totalAmount: 212000 })));
  await assertFails(create(dbFor('customer'), booking('b2','customer', { slotId: 'slot_7', startTime: '18:30', endTime: '20:00' })));
});
test('five catalog services plus history and notification fit access budget', async () => {
  await seed('pitches/pitch/timeSlots/slot_1', {startTime:'06:00',endTime:'07:30',isActive:true,isPeakHour:false,surcharge:0});
  await create(dbFor('other'),booking('other-slot','other',{slotId:'slot_2',startTime:'07:30',endTime:'09:00'}));
  const services = [];
  for (let i = 0; i < 5; i++) {
    await seed(`services/s${i}`, { name: `Service ${i}`, price: 1000, isActive: true });
    services.push({ serviceId: `s${i}`, serviceName: `Service ${i}`, price: 1000 });
  }
  const db = dbFor('customer'), batch = writeBatch(db);
  batch.set(doc(db,'bookings/b1'), booking('b1','customer', { services, totalServicePrice: 5000, totalAmount: 205000 }));
  batch.set(doc(db,dayPath), day(), {merge:true});
  batch.set(doc(db,'users/customer/bookingHistory/b1'), { bookingRef: 'b1' });
  batch.set(doc(db,'fcm_queue/b1'), {
    type: 'new_booking', bookingId: 'b1', customerId: 'customer', processed: false
  });
  await assertSucceeds(batch.commit());
});
test('six add-ons are rejected even when all prices are valid', async () => {
  await seed('services/s', {name:'Ball',price:1000,isActive:true});
  const services = Array(6).fill({serviceId:'s',serviceName:'Ball',price:1000});
  await assertFails(create(dbFor('customer'),booking('b1','customer',{services,totalServicePrice:6000,totalAmount:206000})));
});
test('unmigrated pitch cannot bypass missing legacy locks', async () => {
  await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(),'pitches/pitch'),{bookingPolicyVersion:0}));
  await seed('bookings/legacy',booking('legacy','other'));
  await assertFails(create(dbFor('customer')));
  await assertFails(updateDoc(doc(dbFor('owner'),'pitches/pitch'),{bookingPolicyVersion:2}));
});
test('configured schedule cannot fall back to an absent default slot', async () => {
  await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(),'pitches/pitch'),{useDefaultSlots:false}));
  await assertFails(create(dbFor('customer')));
  await assertFails(updateDoc(doc(dbFor('owner'),'pitches/pitch'),{useDefaultSlots:true}));
});
test('schedule edits require disabling bookings and privileged review', async () => {
  const slot = {startTime:'06:00',endTime:'07:30',isActive:true,isPeakHour:false,surcharge:0};
  await assertFails(setDoc(doc(dbFor('owner'),'pitches/pitch/timeSlots/slot_1'),slot));
  await assertFails(setDoc(doc(dbFor('admin'),'pitches/pitch/timeSlots/slot_1'),slot));
  await assertSucceeds(updateDoc(doc(dbFor('admin'),'pitches/pitch'),{bookingPolicyVersion:0}));
  await assertSucceeds(setDoc(doc(dbFor('admin'),'pitches/pitch/timeSlots/slot_1'),slot));
});
test('owner approval cannot rewrite financial fields', async () => {
  await create(dbFor('customer'));
  await assertFails(transition(dbFor('owner'),'b1','approved',{totalAmount:1}));
  await assertFails(transition(dbFor('owner'),'b1','approved',{paymentStatus:'paid'}));
});
test('past cancellation is denied but manager can reject expired pending booking', async () => {
  const b = booking('b1','customer',{bookingDate:'2020-01-01'});
  await seed('bookings/b1',b);
  await seed('pitches/pitch/availability/2020-01-01',{...day(),date:'2020-01-01'});
  for (const [uid,status,expected] of [['customer','cancelled',assertFails],['owner','rejected',assertSucceeds]]) {
    const db=dbFor(uid), batch=writeBatch(db);
    batch.update(doc(db,'bookings/b1'),{bookingStatus:status,updatedAt:serverTimestamp()});
    batch.set(doc(db,'pitches/pitch/availability/2020-01-01'),{...day(null,'available'),date:'2020-01-01'},{merge:true});
    await expected(batch.commit());
  }
});
test('forged, inactive and nonexistent services are rejected', async () => {
  await seed('services/s1', { name: 'Ball', price: 1000, isActive: true });
  for (const item of [{ serviceId:'s1',serviceName:'Ball',price:1 }, { serviceId:'missing',serviceName:'Ball',price:1000 }]) {
    await assertFails(create(dbFor('customer'), booking('b1','customer', { services:[item],totalServicePrice:item.price,totalAmount:200000+item.price })));
  }
  await seed('services/s1', { name: 'Ball', price: 1000, isActive: false });
  await assertFails(create(dbFor('customer'), booking('b1','customer', { services:[{serviceId:'s1',serviceName:'Ball',price:1000}],totalServicePrice:1000,totalAmount:201000 })));
});
test('owner approves then customer cancels and slot can be rebooked', async () => {
  await assertSucceeds(create(dbFor('customer')));
  await assertSucceeds(transition(dbFor('owner'), 'b1', 'approved'));
  await assertSucceeds(transition(dbFor('customer'), 'b1', 'cancelled'));
  await assertSucceeds(create(dbFor('other'), booking('b2','other')));
  await assertFails(transition(dbFor('customer'), 'b1', 'cancelled'));
  const current = await getDoc(doc(dbFor('other'),dayPath));
  assert.equal(current.data().slots.slot_1.bookingId,'b2');
});
test('owner rejection releases slot and stores reason', async () => {
  await create(dbFor('customer'));
  await assertSucceeds(transition(dbFor('owner'),'b1','rejected',{adminNote:'Unavailable'}));
  await assertSucceeds(create(dbFor('other'), booking('b2','other')));
});
test('cancel cannot change price, payment or identity', async () => {
  await create(dbFor('customer'));
  for (const extra of [{totalAmount:0},{paymentStatus:'paid'},{customerId:'other'},{pitchOwnerId:'customer'},{slotId:'slot_2'}]) {
    await assertFails(transition(dbFor('customer'),'b1','cancelled',extra));
  }
});
test('unrelated owner/customer and blocked owner cannot transition', async () => {
  await create(dbFor('customer'));
  await assertFails(transition(dbFor('stranger'),'b1','approved'));
  await assertFails(transition(dbFor('other'),'b1','cancelled'));
  await assertFails(transition(dbFor('customer'),'b1','approved'));
  await seed('users/owner', {uid:'owner',role:'owner',status:'blocked'});
  await assertFails(transition(dbFor('owner'),'b1','approved'));
});
test('status-only, availability-only, delete and multi-slot tampering fail', async () => {
  const db = dbFor('customer');
  await create(db);
  await assertFails(updateDoc(doc(db,'bookings/b1'),{bookingStatus:'cancelled',updatedAt:serverTimestamp()}));
  await assertFails(setDoc(doc(db,dayPath),day(null,'available'),{merge:true}));
  await assertFails(deleteDoc(doc(db,dayPath)));
  await assertFails(deleteDoc(doc(dbFor('admin'),'bookings/b1')));
  const batch = writeBatch(db);
  batch.update(doc(db,'bookings/b1'),{bookingStatus:'cancelled',updatedAt:serverTimestamp()});
  batch.set(doc(db,dayPath),{...day(null,'available'),slots:{slot_1:{status:'available',bookingId:null},slot_2:{status:'booked',bookingId:'fake'}}},{merge:true});
  await assertFails(batch.commit());
});
test('two writes for same slot have exactly one winner', async () => {
  const results = await Promise.allSettled([create(dbFor('customer')),create(dbFor('other'),booking('b2','other'))]);
  assert.equal(results.filter(r => r.status === 'fulfilled').length,1);
});
test('transaction contention preserves other slots and has one same-slot winner', async () => {
  async function reserve(uid,id) {
    const db = dbFor(uid);
    return runTransaction(db, async tx => {
      const ref = doc(db,dayPath), snap = await tx.get(ref);
      if (snap.exists() && (snap.data().slots?.slot_1?.status ?? 'available') !== 'available') throw new Error('Occupied');
      tx.set(doc(db,'bookings',id),booking(id,uid));
      tx.set(ref,day(id),{merge:true});
    });
  }
  await create(dbFor('customer'),booking('other-slot','customer',{slotId:'slot_2',startTime:'07:30',endTime:'09:00'}));
  const results = await Promise.allSettled([reserve('customer','b1'),reserve('other','b2')]);
  assert.equal(results.filter(r => r.status === 'fulfilled').length,1);
  assert.equal((await getDoc(doc(dbFor('customer'),dayPath))).data().slots.slot_2.bookingId,'other-slot');
});
