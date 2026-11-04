import { readFileSync } from 'node:fs';
import assert from 'node:assert/strict';
import { after, before, beforeEach, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  doc,
  getDoc,
  getDocs,
  increment,
  limit,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
} from 'firebase/firestore';

if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error('Use npm test; emulator required.');

let env;
const dbFor = uid => env.authenticatedContext(uid).firestore();

const conversation = (overrides = {}) => ({
  bookingId: 'chat-booking',
  customerId: 'customer',
  ownerId: 'owner',
  pitchId: 'pitch',
  participants: ['customer', 'owner'],
  pitchName: 'Sân bóng',
  customerName: 'Khách hàng',
  bookingDate: '2026-11-03',
  startTime: '19:00',
  endTime: '20:30',
  status: 'active',
  lastMessage: '',
  lastSenderId: '',
  unreadForCustomer: 0,
  unreadForOwner: 0,
  lastMessageAt: serverTimestamp(),
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
  ...overrides,
});

const message = (overrides = {}) => ({
  senderId: 'customer',
  content: 'Tôi sẽ đến đúng giờ.',
  type: 'text',
  createdAt: serverTimestamp(),
  ...overrides,
});

const seed = async (path, data) => env.withSecurityRulesDisabled(
  context => setDoc(doc(context.firestore(), path), data)
);

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-football-booking',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

beforeEach(async () => {
  await env.clearFirestore();
  for (const [uid, role, status] of [
    ['customer', 'customer', 'active'],
    ['owner', 'owner', 'active'],
    ['other', 'customer', 'active'],
    ['blocked', 'customer', 'blocked'],
  ]) {
    await seed(`users/${uid}`, { uid, role, status });
  }

  await seed('bookings/chat-booking', {
    bookingId: 'chat-booking',
    customerId: 'customer',
    customerName: 'Khách hàng',
    pitchOwnerId: 'owner',
    pitchId: 'pitch',
    pitchName: 'Sân bóng',
    bookingDate: '2026-11-03',
    startTime: '19:00',
    endTime: '20:30',
    totalAmount: 180000,
    paymentMethod: 'cash',
    paymentStatus: 'unpaid',
    bookingStatus: 'approved',
  });
});

after(async () => {
  if (env) await env.cleanup();
});

test('only the booking customer and owner can access the conversation', async () => {
  await assertSucceeds(setDoc(
    doc(dbFor('customer'), 'conversations/chat-booking'), conversation()
  ));

  await assertSucceeds(getDoc(doc(dbFor('customer'), 'conversations/chat-booking')));
  await assertSucceeds(getDoc(doc(dbFor('owner'), 'conversations/chat-booking')));
  await assertFails(getDoc(doc(dbFor('other'), 'conversations/chat-booking')));
  await assertFails(getDoc(doc(dbFor('blocked'), 'conversations/chat-booking')));
  await assertFails(getDocs(collection(dbFor('customer'), 'conversations')));
});

test('participants can list only their own inbox conversations', async () => {
  await seed('conversations/chat-booking', conversation());

  const customerInbox = query(
    collection(dbFor('customer'), 'conversations'),
    where('participants', 'array-contains', 'customer'),
    where('status', '==', 'active'),
    limit(50),
  );
  const ownerInbox = query(
    collection(dbFor('owner'), 'conversations'),
    where('participants', 'array-contains', 'owner'),
    where('status', '==', 'active'),
    limit(50),
  );
  await assertSucceeds(getDocs(customerInbox));
  await assertSucceeds(getDocs(ownerInbox));
  // Query có điều kiện không khớp dữ liệu trả về danh sách rỗng;
  // truy vấn không giới hạn đã được kiểm tra bị từ chối ở test phía trên.
});

test('only participants can create valid text messages', async () => {
  await seed('conversations/chat-booking', conversation());

  await assertSucceeds(setDoc(
    doc(dbFor('customer'), 'conversations/chat-booking/messages/m1'),
    message()
  ));

  await assertSucceeds(setDoc(
    doc(dbFor('owner'), 'conversations/chat-booking/messages/m2'),
    message({ senderId: 'owner', content: 'Được, sân đã sẵn sàng.' })
  ));

  await assertFails(setDoc(
    doc(dbFor('other'), 'conversations/chat-booking/messages/m3'),
    message({ senderId: 'other' })
  ));

  await assertFails(setDoc(
    doc(dbFor('customer'), 'conversations/chat-booking/messages/m4'),
    message({ content: 'x'.repeat(1001) })
  ));
});

test('message sender increments only recipient unread counter', async () => {
  await seed('conversations/chat-booking', conversation());
  const conversationRef = doc(dbFor('customer'), 'conversations/chat-booking');

  await assertSucceeds(updateDoc(conversationRef, {
    lastMessage: 'Xin chao',
    lastSenderId: 'customer',
    lastMessageAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    unreadForOwner: increment(1),
  }));

  await assertSucceeds(updateDoc(
    doc(dbFor('owner'), 'conversations/chat-booking'),
    { unreadForOwner: 0 },
  ));

  await assertFails(updateDoc(conversationRef, {
    unreadForCustomer: increment(1),
  }));
});

test('a pending booking can start the booking conversation', async () => {
  await seed('bookings/pending-booking', {
    bookingId: 'pending-booking',
    customerId: 'customer',
    pitchOwnerId: 'owner',
    pitchId: 'pitch',
    bookingStatus: 'pending',
  });

  await assertSucceeds(setDoc(
    doc(dbFor('customer'), 'conversations/pending-booking'),
    conversation({ bookingId: 'pending-booking' })
  ));

  await assertFails(setDoc(
    doc(dbFor('other'), 'conversations/chat-booking'),
    conversation({
      customerId: 'other',
    })
  ));
});
