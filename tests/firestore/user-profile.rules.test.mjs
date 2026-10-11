import { readFileSync } from 'node:fs';
import { after, before, beforeEach, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteDoc,
  deleteField,
  doc,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

// Refuse to run without the emulator. No production project/credentials needed.
if (!process.env.FIRESTORE_EMULATOR_HOST) {
  throw new Error('Run npm test to start the local Firestore emulator.');
}

let environment;
const profile = (uid, role = 'customer', status = 'active') => ({
  uid,
  email: `${uid}@example.test`,
  displayName: 'Test user',
  phoneNumber: '',
  avatarUrl: '',
  fcmToken: '',
  role,
  status,
  createdAt: serverTimestamp(),
  updatedAt: serverTimestamp(),
});
const database = uid => environment.authenticatedContext(uid, {
  email: `${uid}@example.test`,
}).firestore();

before(async () => {
  environment = await initializeTestEnvironment({
    projectId: 'demo-football-booking',
    firestore: {
      rules: readFileSync(new URL('../../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();
  await environment.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await Promise.all([
      ['admin', 'admin', 'active'],
      ['customer', 'customer', 'active'],
      ['pending', 'owner_pending', 'active'],
      ['owner', 'owner', 'active'],
      ['blocked', 'customer', 'blocked'],
      ['blocked-admin', 'admin', 'blocked'],
    ].map(([uid, role, status]) => setDoc(doc(db, 'users', uid), profile(uid, role, status))));
  });
});

after(async () => {
  if (environment) await environment.cleanup();
});

test('customer registration remains allowed', async () => {
  await assertSucceeds(setDoc(doc(database('new'), 'users/new'), profile('new')));
});

test('owner registration can request approval, not become an owner', async () => {
  await assertSucceeds(setDoc(doc(database('new'), 'users/new'), profile('new', 'owner_pending')));
});

for (const role of ['admin', 'owner', 'unexpected']) {
  test(`self-registration cannot create role ${role}`, async () => {
    await assertFails(setDoc(doc(database('new'), 'users/new'), profile('new', role)));
  });
}

test('registration requires matching uid and active status', async () => {
  const db = database('new');
  await assertFails(setDoc(doc(db, 'users/new'), profile('someone-else')));
  await assertFails(setDoc(doc(db, 'users/new'), profile('new', 'customer', 'blocked')));
  const missingStatus = profile('new');
  delete missingStatus.status;
  await assertFails(setDoc(doc(db, 'users/new'), missingStatus));
});

test('anonymous users and other users cannot create a profile for someone else', async () => {
  await assertFails(setDoc(doc(environment.unauthenticatedContext().firestore(), 'users/new'), profile('new')));
  await assertFails(setDoc(doc(database('customer'), 'users/new'), profile('new')));
});

test('an email containing admin grants no privileged role', async () => {
  const db = environment.authenticatedContext('new', { email: 'admin.owner@example.test' }).firestore();
  await assertSucceeds(setDoc(doc(db, 'users/new'), profile('new')));
  await assertFails(updateDoc(doc(db, 'users/new'), { role: 'admin' }));
});

for (const uid of ['customer', 'pending', 'owner']) {
  test(`${uid} cannot change its own role`, async () => {
    const ref = doc(database(uid), 'users', uid);
    await assertFails(updateDoc(ref, { role: 'admin' }));
    await assertFails(updateDoc(ref, { role: 'owner' === uid ? 'customer' : 'owner' }));
  });
}

test('blocked customer cannot unblock itself or delete/recreate its profile', async () => {
  const ref = doc(database('blocked'), 'users/blocked');
  await assertFails(updateDoc(ref, { status: 'active' }));
  await assertFails(deleteDoc(ref));
  await assertFails(setDoc(ref, profile('blocked')));
});

test('deleting protected fields or replacing a profile cannot bypass the update allowlist', async () => {
  const ref = doc(database('customer'), 'users/customer');
  for (const field of ['role', 'status', 'uid', 'email', 'createdAt']) {
    await assertFails(updateDoc(ref, { [field]: deleteField() }));
  }
  await assertFails(updateDoc(ref, { uid: 'admin' }));
  await assertFails(setDoc(ref, { displayName: 'Replacement' }));
  await assertFails(updateDoc(ref, { customAdminFlag: true }));
});

test('own display/contact/avatar updates and FCM registration still work', async () => {
  await assertSucceeds(updateDoc(doc(database('customer'), 'users/customer'), {
    displayName: 'Updated name',
    phoneNumber: '0900000000',
    avatarUrl: 'https://example.test/avatar.png',
    fcmToken: 'test-device-token',
    updatedAt: serverTimestamp(),
  }));
});

test('a customer cannot edit another profile or approve an owner', async () => {
  const db = database('customer');
  await assertFails(updateDoc(doc(db, 'users/owner'), { displayName: 'Changed' }));
  await assertFails(updateDoc(doc(db, 'users/pending'), { role: 'owner' }));
});

test('an active admin can approve a pending owner and block/unblock accounts', async () => {
  const db = database('admin');
  await assertSucceeds(updateDoc(doc(db, 'users/pending'), { role: 'owner', updatedAt: serverTimestamp() }));
  await assertSucceeds(updateDoc(doc(db, 'users/customer'), { status: 'blocked' }));
  await assertSucceeds(updateDoc(doc(db, 'users/blocked'), { status: 'active' }));
});

test('admin updates still reject invalid roles/status and identity rewrites', async () => {
  const ref = doc(database('admin'), 'users/customer');
  await assertFails(updateDoc(ref, { role: 'superadmin' }));
  await assertFails(updateDoc(ref, { status: 'anything' }));
  await assertFails(updateDoc(ref, { uid: 'admin' }));
});

test('a blocked admin cannot promote users or restore its own admin access', async () => {
  const db = database('blocked-admin');
  await assertFails(updateDoc(doc(db, 'users/pending'), { role: 'owner' }));
  await assertFails(updateDoc(doc(db, 'users/blocked-admin'), { status: 'active' }));
});

test('legacy admin profile without status can still manage accounts', async () => {
  await environment.withSecurityRulesDisabled(async context => {
    await updateDoc(doc(context.firestore(), 'users/admin'), { status: deleteField() });
  });
  await assertSucceeds(updateDoc(doc(database('admin'), 'users/blocked'), { status: 'active' }));
});

test('batching self-promotion with a privileged write cannot bypass authorization', async () => {
  const db = database('customer');
  const batch = writeBatch(db);
  batch.update(doc(db, 'users/customer'), { role: 'admin' });
  batch.set(doc(db, 'employees/unauthorized'), { fullName: 'Unauthorized' });
  await assertFails(batch.commit());
});
