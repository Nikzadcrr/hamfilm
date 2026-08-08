// ---------- احراز هویت (JWT + bcrypt) ----------
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import crypto from 'crypto';

export function signToken(payload, ttlSec = 30 * 24 * 3600) {
  return jwt.sign(payload, process.env.JWT_SECRET, { expiresIn: ttlSec });
}

export function verifyToken(token) {
  try { return jwt.verify(token, process.env.JWT_SECRET); } catch { return null; }
}

export function hashPassword(pw) { return bcrypt.hashSync(pw, 10); }
export function verifyPassword(pw, hash) { return bcrypt.compareSync(pw, hash); }

export function uid(len = 16) {
  return crypto.randomBytes(len).toString('base64url').slice(0, len);
}

export function roomCode(len = 6) {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let s = '';
  for (let i = 0; i < len; i++) s += chars[crypto.randomInt(chars.length)];
  return s;
}
