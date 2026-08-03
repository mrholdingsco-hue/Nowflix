import { redirect } from "next/navigation";
import { hasValidSession } from "@/lib/session";
import { NavShell } from "@/components/NavShell";

export const dynamic = "force-dynamic";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  if (!(await hasValidSession())) redirect("/login");
  return <NavShell>{children}</NavShell>;
}
