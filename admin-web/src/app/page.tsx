import { redirect } from "next/navigation";
import { hasValidSession } from "@/lib/session";

export const dynamic = "force-dynamic";

export default async function Home() {
  redirect((await hasValidSession()) ? "/parts" : "/login");
}
