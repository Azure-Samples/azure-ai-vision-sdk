// app/middleware.js
import { NextResponse } from 'next/server';

export function middleware(request) {
  const response = NextResponse.next();
  console.log('Middleware executed'); // Console log for testing
  response.headers.set('Cross-Origin-Opener-Policy', 'same-origin');
  response.headers.set('Cross-Origin-Embedder-Policy', 'require-corp');
  return response;
}
