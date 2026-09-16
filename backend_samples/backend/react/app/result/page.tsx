import type { Metadata } from 'next';
import SessionPage from '../_components/SessionPage';
import { smartBannerMetadata } from '../_lib/session_landing';

// Same landing page as /native, but at a path the App Link filter does NOT
// match, so the app's post-check callbackUrl always opens a browser here
// (showing the live result) instead of re-launching the app.
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

export default async function ResultPage({ searchParams }: { searchParams: SearchParams }) {
  const sid = readSid(await searchParams);
  return <SessionPage sessionId={sid} path="/result" />;
}
