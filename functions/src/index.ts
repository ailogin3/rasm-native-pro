/**
 * Rasm Native — Pro-plan Cloud Functions.
 *
 * These are the ONLY places that ever read or write the `admins` collection.
 * The Android app no longer touches `admins` directly — see FirebaseRepository.kt
 * (checkIsAdmin / addAdmin / removeAdmin / emailBelongsToMember), which now call
 * these callable functions instead of talking to Firestore for these operations.
 *
 * Deploy with: firebase deploy --only functions
 * Requires the Blaze (pay-as-you-go) plan — Cloud Functions cannot deploy on Spark.
 *
 * IMPORTANT — bootstrapping your first admin:
 * addAdmin() below refuses to run unless the CALLER is already an admin. That's
 * correct once you have at least one admin, but it means there is no callable path
 * to create the very first admin (nothing to bootstrap from). Before anyone can use
 * "Add Admin" in the app, manually create one document, once, directly in the
 * Firebase Console:
 *   Firestore Database → Start collection → id: admins
 *     → Document ID: <your-email-lowercased>
 *     → Field: isAdmin (boolean) = true
 * After that, that person can add/remove further admins from inside the app.
 */

import * as admin from "firebase-admin";
import { onCall, HttpsError } from "firebase-functions/v2/https";

admin.initializeApp();
const db = admin.firestore();

function normalizeEmail(raw: unknown): string {
  if (typeof raw !== "string" || raw.trim().length === 0) {
    throw new HttpsError("invalid-argument", "A valid 'email' string is required.");
  }
  return raw.trim().toLowerCase();
}

async function isCallerAdmin(callerEmail: string | undefined): Promise<boolean> {
  if (!callerEmail) return false;
  const doc = await db.collection("admins").doc(callerEmail.toLowerCase()).get();
  return doc.exists && doc.get("isAdmin") === true;
}

/**
 * Returns { isAdmin: boolean } for the given email.
 * Requires the caller to be signed in (any signed-in user can check admin status —
 * this only reveals a true/false flag, not the full admin list).
 */
export const checkIsAdmin = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  const email = normalizeEmail(request.data?.email);
  const doc = await db.collection("admins").doc(email).get();
  const isAdmin = doc.exists && doc.get("isAdmin") === true;
  return { isAdmin };
});

/**
 * Returns { belongsToMember: boolean } for the given email.
 * Deliberately callable WITHOUT authentication — this runs during sign-up, before
 * the user has an account yet, exactly like the original client-side check did.
 * It only ever returns a boolean, never member details, so it doesn't leak data.
 */
export const emailBelongsToMember = onCall(async (request) => {
  const email = normalizeEmail(request.data?.email);
  const snapshot = await db.collection("members").get();
  const belongsToMember = snapshot.docs.some(
    (doc) => (doc.get("email") as string | undefined)?.trim().toLowerCase() === email
  );
  return { belongsToMember };
});

/**
 * Grants admin status to an email. Caller must already be an admin.
 * See the bootstrapping note at the top of this file for the very first admin.
 */
export const addAdmin = onCall(async (request) => {
  if (!request.auth?.token?.email) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  const callerEmail = request.auth.token.email as string;
  if (!(await isCallerAdmin(callerEmail))) {
    throw new HttpsError("permission-denied", "Only an existing admin can add another admin.");
  }
  const email = normalizeEmail(request.data?.email);
  await db.collection("admins").doc(email).set({ isAdmin: true }, { merge: true });
  return { success: true };
});

/**
 * Revokes admin status from an email. Caller must already be an admin.
 * An admin may remove any admin, including themselves (mirrors the original
 * client-side behavior — add a self-protection check here later if you want to
 * prevent accidentally removing the last admin).
 */
export const removeAdmin = onCall(async (request) => {
  if (!request.auth?.token?.email) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  const callerEmail = request.auth.token.email as string;
  if (!(await isCallerAdmin(callerEmail))) {
    throw new HttpsError("permission-denied", "Only an existing admin can remove an admin.");
  }
  const email = normalizeEmail(request.data?.email);
  await db.collection("admins").doc(email).delete();
  return { success: true };
});
