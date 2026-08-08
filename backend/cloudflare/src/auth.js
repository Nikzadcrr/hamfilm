// ---------- ابزارهای احراز هویت (JWT با WebCrypto) ----------
const enc = new TextEncoder();
const dec = new TextDecoder();

function b64url(bytes) {
  let s = '';
  new Uint8Array(bytes).forEach(b => s += String.fromCharCode(b));
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function b64urlDecode(str) {
  const b = atob(str.replace(/-/g, '+').replace(/_/g, '/'));
  const arr = new Uint8Array(b.length);
  for (let i = 0; i < b.length; i++) arr[i] = b.charCodeAt(i);
  return arr;
}
async function hmac(data, secret) {
  const key = await crypto.subtle.importKey('raw', enc.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return crypto.subtle.sign('HMAC', key, data);
}

export async function signToken(payload, secret, ttlSec = 30 * 24 * 3600) {
  const header = b64url(enc.encode(JSON.stringify({ alg: 'HS256', typ: 'JWT' })));
  const body = b64url(enc.encode(JSON.stringify({ ...payload, exp: Math.floor(Date.now() / 1000) + ttlSec })));
  const sig = b64url(await hmac(enc.encode(`${header}.${body}`), secret));
  return `${header}.${body}.${sig}`;
}

export async function verifyToken(token, secret) {
  try {
    const [h, b, s] = token.split('.');
    const expected = b64url(await hmac(enc.encode(`${h}.${b}`), secret));
    if (expected !== s) return null;
    const payload = JSON.parse(dec.decode(b64urlDecode(b)));
    if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch { return null; }
}

export async function hashPassword(password, salt = crypto.randomUUID()) {
  const key = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: enc.encode(salt), iterations: 100_000, hash: 'SHA-256' },
    key, 256
  );
  return `${salt}:${b64url(bits)}`;
}

export async function verifyPassword(password, stored) {
  const [salt] = stored.split(':');
  return (await hashPassword(password, salt)) === stored;
}

export function uid(len = 16) {
  const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let s = '';
  const arr = crypto.getRandomValues(new Uint8Array(len));
  arr.forEach(b => s += chars[b % chars.length]);
  return s;
}

export function roomCode(len = 6) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // بدون کاراکترهای گیج‌کننده
  let s = '';
  const arr = crypto.getRandomValues(new Uint8Array(len));
  arr.forEach(b => s += chars[b % chars.length]);
  return s;
}
