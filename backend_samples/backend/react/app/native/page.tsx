import type { Metadata } from 'next';
import SessionPage from '../_components/SessionPage';
import { smartBannerMetadata } from '../_lib/session_landing';

// The App Link target: when the app is installed Android/iOS intercept this and
// open the app; otherwise the browser falls back to this landing page.
export const dynamic = 'force-dynamic';

type SearchParams = Promise<{ [key: string]: string | string[] | undefined }>;

function readSid(sp: { [key: string]: string | string[] | undefined }): string {
  return typeof sp.s === 'string' ? sp.s : '';
}

export async function generateMetadata({
  searchParams,
}: {
  searchParams: SearchParams;
}): Promise<Metadata> {
  return smartBannerMetadata(readSid(await searchParams));
}

export default async function NativePage({ searchParams }: { searchParams: SearchParams }) {
  const sid = readSid(await searchParams);
  return <SessionPage sessionId={sid} path="/native" />;
}
