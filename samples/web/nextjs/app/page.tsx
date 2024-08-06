import FaceAnalyzerSampleClient from "@/components/FaceAnalyzerSampleClient";

export default function Home() {
  return (
    <main className="flex flex-col m-0 min-w-full h-[100vh] overflow-hidden overflow-y-auto justify-between">
      {/* Per the instructions of Next.js App Directory, interactive components like buttons and file uploads are separated from static components to separate client and server components*/}
      <FaceAnalyzerSampleClient />
    </main>
  );
}
