"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState } from "react";

const NAV = [
  { href: "/parts", label: "파트 관리" },
  { href: "/settings", label: "설정" },
  { href: "/status", label: "상태" },
];

export function NavShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [loggingOut, setLoggingOut] = useState(false);

  async function logout() {
    setLoggingOut(true);
    await fetch("/api/logout", { method: "POST" });
    router.push("/login");
  }

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-20 border-b border-line bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="inline-flex h-7 items-center rounded-lg bg-ink px-2.5 text-sm font-extrabold tracking-tight text-white">
              NOWFLIX
            </span>
            <span className="hidden text-sm font-semibold text-subtle sm:inline">관리자</span>
          </div>
          <button
            onClick={logout}
            disabled={loggingOut}
            className="rounded-lg px-3 py-1.5 text-sm font-medium text-subtle hover:bg-canvas hover:text-ink"
          >
            로그아웃
          </button>
        </div>
        <nav className="mx-auto flex max-w-4xl gap-1 px-2 sm:px-4">
          {NAV.map((item) => {
            const active = pathname === item.href || pathname.startsWith(item.href + "/");
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`-mb-px border-b-2 px-3 py-2.5 text-sm font-semibold transition-colors ${
                  active
                    ? "border-brand text-brand"
                    : "border-transparent text-subtle hover:text-ink"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
      </header>
      <main className="mx-auto max-w-4xl px-4 py-6 sm:py-8">{children}</main>
    </div>
  );
}
