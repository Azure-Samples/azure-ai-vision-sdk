import { NextResponse } from 'next/server';

// Permissive CORS so the native samples (iOS/Android) can call the /api/*
// attestation + session endpoints from the app. GET/POST/OPTIONS only.
export function proxy() {
  const response = NextResponse.next();
  response.headers.set('Access-Control-Allow-Origin', '*');
  response.headers.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  response.headers.set(
    'Access-Control-Allow-Headers',
    'Authorization, Content-Type'
  );
  return response;
}
