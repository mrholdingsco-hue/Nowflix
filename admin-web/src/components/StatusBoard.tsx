"use client";

import { useCallback, useEffect, useState } from "react";
import { Button, Card, Banner, Spinner } from "./ui";
import type { PartStatus } from "@/lib/types";

export function StatusBoard() {
  const [statuses, setStatuses] = useState<PartStatus[] | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch("/api/status");
      if (res.ok) setStatuses((await res.json()).statuses);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const problems = statuses?.filter((s) => !s.ok || s.warning) ?? [];

  return (
    <div>
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-ink">상태</h1>
          <p className="mt-0.5 text-sm text-subtle">각 파트의 유튜브 재생목록을 실시간으로 확인해요.</p>
        </div>
        <Button variant="secondary" onClick={load} disabled={loading}>
          {loading ? <Spinner /> : "새로고침"}
        </Button>
      </div>

      {statuses && problems.length === 0 && !loading && (
        <div className="mb-4">
          <Banner tone="ok">모든 재생목록이 정상이에요. 태블릿이 정상적으로 콘텐츠를 받고 있어요.</Banner>
        </div>
      )}
      {problems.length > 0 && (
        <div className="mb-4">
          <Banner tone="warn">
            확인이 필요한 재생목록이 {problems.length}개 있어요. 아래에서 자세히 확인해주세요.
          </Banner>
        </div>
      )}

      {loading && !statuses && (
        <Card>
          <p className="flex items-center justify-center gap-2 text-sm text-subtle">
            <Spinner /> 재생목록 상태를 확인하고 있어요…
          </p>
        </Card>
      )}

      <div className="space-y-3">
        {statuses?.map((s) => (
          <Card key={s.id} className="!p-4">
            <div className="flex items-center justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h3 className="truncate font-semibold text-ink">{s.title}</h3>
                  {!s.isActive && (
                    <span className="flex-none rounded-md bg-canvas px-1.5 py-0.5 text-[11px] font-medium text-subtle">
                      숨김
                    </span>
                  )}
                </div>
                <p className="mt-0.5 text-sm text-subtle">
                  {s.ok ? `영상 ${s.videoCount}개` : "영상 수 확인 불가"}
                </p>
              </div>
              <span
                className={`flex-none rounded-full px-3 py-1 text-sm font-semibold ${
                  s.ok && !s.warning
                    ? "bg-emerald-50 text-emerald-700"
                    : s.ok
                      ? "bg-amber-50 text-amber-700"
                      : "bg-red-50 text-red-700"
                }`}
              >
                {s.ok && !s.warning ? "정상" : s.ok ? "주의" : "경고"}
              </span>
            </div>
            {s.warning && <p className="mt-2 text-sm text-warn">{s.warning}</p>}
          </Card>
        ))}
      </div>
    </div>
  );
}
