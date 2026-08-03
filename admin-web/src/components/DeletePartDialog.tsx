"use client";

import { useState } from "react";
import { Modal } from "./Modal";
import { Button, Input, Banner, Spinner } from "./ui";
import type { Part } from "@/lib/types";

/** Deleting requires typing the part's exact name — guards against slips. */
export function DeletePartDialog({
  part,
  onClose,
  onDeleted,
}: {
  part: Part;
  onClose: () => void;
  onDeleted: () => void;
}) {
  const [confirmText, setConfirmText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const matches = confirmText.trim() === part.title.trim();

  async function del() {
    setBusy(true);
    setError(null);
    try {
      const res = await fetch(`/api/parts/${part.id}`, { method: "DELETE" });
      if (!res.ok) {
        const data = await res.json().catch(() => ({}));
        setError(data.error ?? "삭제하지 못했어요.");
        setBusy(false);
        return;
      }
      onDeleted();
    } catch {
      setError("삭제 중 연결 문제가 생겼어요.");
      setBusy(false);
    }
  }

  return (
    <Modal title="파트 삭제" onClose={onClose}>
      <div className="space-y-4">
        <Banner tone="warn">
          삭제 대신 <b>노출 끄기</b>를 권해요. 노출을 끄면 태블릿에서만 숨겨지고 나중에 다시 켤 수 있어요.
          삭제하면 이미지까지 완전히 지워지고 되돌릴 수 없어요.
        </Banner>
        <p className="text-sm text-ink">
          정말 삭제하려면 아래에 파트 이름 <b className="text-danger">{part.title}</b> 을(를) 그대로 입력해주세요.
        </p>
        <Input
          value={confirmText}
          onChange={(e) => setConfirmText(e.target.value)}
          placeholder={part.title}
          autoFocus
        />
        {error && <Banner tone="danger">{error}</Banner>}
        <div className="flex justify-end gap-2 pt-1">
          <Button variant="ghost" type="button" onClick={onClose}>
            취소
          </Button>
          <Button variant="danger" type="button" onClick={del} disabled={!matches || busy}>
            {busy ? <Spinner /> : "영구 삭제"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
