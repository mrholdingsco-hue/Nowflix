"use client";

import { useCallback, useEffect, useState } from "react";
import { Button, Card, Banner, Spinner } from "./ui";
import { PartDialog } from "./PartDialog";
import { DeletePartDialog } from "./DeletePartDialog";
import type { Part, PartStatus } from "@/lib/types";

export function PartsManager({ initialParts }: { initialParts: Part[] }) {
  const [parts, setParts] = useState<Part[]>(initialParts);
  const [statuses, setStatuses] = useState<Record<string, PartStatus>>({});
  const [statusLoading, setStatusLoading] = useState(true);
  const [editing, setEditing] = useState<Part | "new" | null>(null);
  const [deleting, setDeleting] = useState<Part | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const refreshParts = useCallback(async () => {
    const res = await fetch("/api/parts");
    if (res.ok) setParts((await res.json()).parts);
  }, []);

  const refreshStatus = useCallback(async () => {
    setStatusLoading(true);
    try {
      const res = await fetch("/api/status");
      if (res.ok) {
        const list: PartStatus[] = (await res.json()).statuses;
        setStatuses(Object.fromEntries(list.map((s) => [s.id, s])));
      }
    } finally {
      setStatusLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshStatus();
  }, [refreshStatus, parts]);

  async function toggleActive(part: Part) {
    setBusyId(part.id);
    const form = new FormData();
    form.set("isActive", String(!part.is_active));
    await fetch(`/api/parts/${part.id}`, { method: "PATCH", body: form });
    await refreshParts();
    setBusyId(null);
  }

  async function move(part: Part, direction: "up" | "down") {
    setBusyId(part.id);
    await fetch(`/api/parts/${part.id}/move`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ direction }),
    });
    await refreshParts();
    setBusyId(null);
  }

  return (
    <div>
      <div className="mb-5 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-ink">파트 관리</h1>
          <p className="mt-0.5 text-sm text-subtle">태블릿 메인 화면에 보이는 콘텐츠 목록이에요.</p>
        </div>
        <Button onClick={() => setEditing("new")}>+ 파트 추가</Button>
      </div>

      {parts.length === 0 && (
        <Card>
          <p className="text-center text-sm text-subtle">아직 파트가 없어요. ‘파트 추가’로 시작해보세요.</p>
        </Card>
      )}

      <div className="space-y-3">
        {parts.map((part, i) => {
          const st = statuses[part.id];
          return (
            <Card key={part.id} className="!p-3 sm:!p-4">
              <div className="flex items-center gap-3 sm:gap-4">
                {/* Thumbnail */}
                {part.thumbnail_url ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={part.thumbnail_url}
                    alt=""
                    className="h-20 w-14 flex-none rounded-lg border border-line object-cover"
                  />
                ) : (
                  <div className="flex h-20 w-14 flex-none items-center justify-center rounded-lg border border-dashed border-line text-[10px] text-subtle">
                    이미지<br />없음
                  </div>
                )}

                {/* Info */}
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <h3 className="truncate font-semibold text-ink">{part.title}</h3>
                    {!part.is_active && (
                      <span className="flex-none rounded-md bg-canvas px-1.5 py-0.5 text-[11px] font-medium text-subtle">
                        숨김
                      </span>
                    )}
                  </div>
                  <div className="mt-0.5 text-sm text-subtle">
                    {statusLoading && !st ? (
                      <span className="inline-flex items-center gap-1 text-subtle">
                        <Spinner className="!h-3 !w-3" /> 영상 수 확인 중…
                      </span>
                    ) : st?.ok ? (
                      <span>영상 {st.videoCount}개</span>
                    ) : (
                      <span className="text-danger">확인 필요</span>
                    )}
                  </div>
                  {st?.warning && (
                    <p className="mt-1 text-xs text-warn">{st.warning}</p>
                  )}
                </div>

                {/* Reorder */}
                <div className="flex flex-none flex-col gap-1">
                  <button
                    onClick={() => move(part, "up")}
                    disabled={i === 0 || busyId === part.id}
                    className="rounded-md border border-line px-2 py-0.5 text-sm text-subtle hover:bg-canvas disabled:opacity-30"
                    aria-label="위로"
                  >
                    ↑
                  </button>
                  <button
                    onClick={() => move(part, "down")}
                    disabled={i === parts.length - 1 || busyId === part.id}
                    className="rounded-md border border-line px-2 py-0.5 text-sm text-subtle hover:bg-canvas disabled:opacity-30"
                    aria-label="아래로"
                  >
                    ↓
                  </button>
                </div>
              </div>

              {/* Actions */}
              <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-line pt-3">
                <button
                  onClick={() => toggleActive(part)}
                  disabled={busyId === part.id}
                  className={`inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium ${
                    part.is_active ? "bg-emerald-50 text-emerald-700" : "bg-canvas text-subtle"
                  }`}
                >
                  <span className={`h-2 w-2 rounded-full ${part.is_active ? "bg-emerald-500" : "bg-gray-400"}`} />
                  {part.is_active ? "노출 중" : "노출 꺼짐"}
                </button>
                <div className="ml-auto flex gap-2">
                  <Button variant="secondary" onClick={() => setEditing(part)}>
                    수정
                  </Button>
                  <Button variant="ghost" className="!text-danger" onClick={() => setDeleting(part)}>
                    삭제
                  </Button>
                </div>
              </div>
            </Card>
          );
        })}
      </div>

      {editing && (
        <PartDialog
          part={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={async () => {
            setEditing(null);
            await refreshParts();
          }}
        />
      )}
      {deleting && (
        <DeletePartDialog
          part={deleting}
          onClose={() => setDeleting(null)}
          onDeleted={async () => {
            setDeleting(null);
            await refreshParts();
          }}
        />
      )}
    </div>
  );
}
