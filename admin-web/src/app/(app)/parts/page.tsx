import { listParts } from "@/lib/db";
import { PartsManager } from "@/components/PartsManager";

export const dynamic = "force-dynamic";

export default async function PartsPage() {
  const parts = await listParts();
  return <PartsManager initialParts={parts} />;
}
