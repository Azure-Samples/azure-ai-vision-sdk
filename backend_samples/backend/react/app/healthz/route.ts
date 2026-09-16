import { NextResponse } from 'next/server';

// Simple health probe for App Service / load balancers.
export const dynamic = 'force-dynamic';

export async function GET() {
  return NextResponse.json({ status: 'ok' });
}
