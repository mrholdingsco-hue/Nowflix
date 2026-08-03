"use client";

import { useRef, useState } from "react";
import { Modal } from "./Modal";
import { Button, Field, Input, Banner, Spinner } from "./ui";
import type { Part } from "@/lib/types";

interface VerifyState {
  status: "idle" | "checking" | "ok" | "error";
  message?: string;
  count?: number;
}

export function PartDialog({
  part,
  onClose,
  onSaved,
}: {
  part: Part | null; // null = add new
  onClose: () => void;
  onSaved: () => void;
}) {
  const isNew = part === null;
  const [title, setTitle] = useState(part?.title ?? "");
  const [url, setUrl] = useState("");
  const [verify, setVerify] = useState<VerifyState>({ status: "idle" });
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<string | null>(part?.thumbnail_url ?? null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  async function checkPlaylist() {
    if (!url.trim()) return;
    setVerify({ status: "checking" });
    try {
      const res = await fetch("/api/parts/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url }),
      });
      const data = await res.json();
      if (!res.ok) {
        setVerify({ status: "error", message: data.error });
        return;
      }
      setVerify({ status: "ok", message: data.title, count: data.itemCount });
      if (!title.trim()) setTitle(data.title);
    } catch {
      setVerify({ status: "error", message: "확인 중 문제가 생겼어요. 잠시 후 다시 시도해주세요." });
    }
  }

  function pickFile(f: File | null) {
    setFile(f);
    if (f) setPreview(URL.createObjectURL(f));
  }

  async function save() {
    setError(null);
    // A new part needs a verified playlist; editing may keep the existing one.
    if (isNew && verify.status !== "ok") {
      setError("먼저 재생목록 주소를 확인해주세요.");
      return;
    }
    setSaving(true);
    const form = new FormData();
    form.set("title", title);
    if (url.trim()) form.set("playlistUrl", url);
    if (file) form.set("image", file);

    try {
      const res = await fetch(isNew ? "/api/parts" : `/api/parts/${part!.id}`, {
        method: isNew ? "POST" : "PATCH",
        body: form,
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error ?? "저장하지 못했어요.");
        setSaving(false);
        return;
      }
      if (data.imageWarning) {
        // Part saved but image failed — inform and still close after a beat.
        setError(`파트는 저장됐지만 이미지는 저장하지 못했어요: ${data.imageWarning}`);
        setSaving(false);
        setTimeout(onSaved, 1500);
        return;
      }
      onSaved();
    } catch {
      setError("저장 중 연결 문제가 생겼어요. 잠시 후 다시 시도해주세요.");
      setSaving(false);
    }
  }

  return (
    <Modal title={isNew ? "파트 추가" : "파트 수정"} onClose={onClose}>
      <div className="space-y-4">
        <Field label="파트 이름">
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="예: 괜찮Knee TV" />
        </Field>

        <Field
          label="유튜브 재생목록 주소"
          hint={isNew ? "재생목록 URL을 붙여넣고 ‘확인’을 눌러 실제로 있는지 검사해요." : "바꿀 때만 새 주소를 붙여넣고 확인하세요. 비워두면 그대로 둡니다."}
        >
          <div className="flex gap-2">
            <Input
              value={url}
              onChange={(e) => {
                setUrl(e.target.value);
                setVerify({ status: "idle" });
              }}
              placeholder="https://www.youtube.com/playlist?list=..."
            />
            <Button variant="secondary" type="button" onClick={checkPlaylist} disabled={!url.trim() || verify.status === "checking"}>
              {verify.status === "checking" ? <Spinner /> : "확인"}
            </Button>
          </div>
        </Field>

        {verify.status === "ok" && (
          <Banner tone="ok">
            재생목록을 찾았어요: <b>{verify.message}</b> · 영상 {verify.count}개
          </Banner>
        )}
        {verify.status === "error" && <Banner tone="danger">{verify.message}</Banner>}

        <Field label="포스터 이미지" hint="JPG 또는 PNG, 5MB 이하. 세로로 긴 포스터가 가장 잘 보여요.">
          <div className="flex items-center gap-3">
            {preview ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={preview} alt="" className="h-24 w-16 rounded-lg border border-line object-cover" />
            ) : (
              <div className="flex h-24 w-16 items-center justify-center rounded-lg border border-dashed border-line text-xs text-subtle">
                없음
              </div>
            )}
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png"
              className="hidden"
              onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
            />
            <Button variant="secondary" type="button" onClick={() => fileRef.current?.click()}>
              이미지 선택
            </Button>
          </div>
        </Field>

        {error && <Banner tone="danger">{error}</Banner>}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" type="button" onClick={onClose}>
            취소
          </Button>
          <Button type="button" onClick={save} disabled={saving || !title.trim()}>
            {saving ? <Spinner /> : "저장"}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
