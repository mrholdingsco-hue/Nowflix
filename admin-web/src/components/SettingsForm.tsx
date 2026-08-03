"use client";

import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { Button, Card, Field, Input, Banner, Spinner } from "./ui";

const REFLECT_NOTE = "저장하면 태블릿에는 최대 30분 안에 반영돼요.";

function Section({
  title,
  desc,
  children,
}: {
  title: string;
  desc?: string;
  children: React.ReactNode;
}) {
  return (
    <Card>
      <h2 className="text-base font-bold text-ink">{title}</h2>
      {desc && <p className="mt-0.5 text-sm text-subtle">{desc}</p>}
      <div className="mt-4 space-y-3">{children}</div>
    </Card>
  );
}

type Msg = { tone: "ok" | "danger"; text: string } | null;

export function SettingsForm({
  initialIdle,
  initialHeader,
  passwordIsDefault,
}: {
  initialIdle: number;
  initialHeader: string;
  passwordIsDefault: boolean;
}) {
  const searchParams = useSearchParams();
  const promptPw = searchParams.get("changePw") === "1" || passwordIsDefault;

  return (
    <div className="space-y-5">
      <div>
        <h1 className="text-xl font-bold text-ink">설정</h1>
        <p className="mt-0.5 text-sm text-subtle">{REFLECT_NOTE}</p>
      </div>

      {promptPw && (
        <Banner tone="warn">
          보안을 위해 처음 비밀번호(기본값)를 지금 바꿔주세요. 아래 <b>웹 로그인 비밀번호</b>에서 변경할 수 있어요.
        </Banner>
      )}

      <IdleSection initial={initialIdle} />
      <HeaderSection initial={initialHeader} />
      <PinSection />
      <PasswordSection highlight={promptPw} />
    </div>
  );
}

function IdleSection({ initial }: { initial: number }) {
  const [value, setValue] = useState(String(initial));
  const [msg, setMsg] = useState<Msg>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true);
    setMsg(null);
    const res = await fetch("/api/settings", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idleReturnSeconds: Number(value) }),
    });
    const data = await res.json();
    setMsg(res.ok ? { tone: "ok", text: "저장했어요. " + REFLECT_NOTE } : { tone: "danger", text: data.error });
    setBusy(false);
  }

  return (
    <Section title="메인 복귀 시간" desc="아무도 만지지 않으면 이 시간 뒤에 메인 화면으로 돌아가요. (30~600초)">
      <Field label="시간(초)">
        <div className="flex gap-2">
          <Input
            type="number"
            min={30}
            max={600}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            className="max-w-40"
          />
          <Button onClick={save} disabled={busy}>
            {busy ? <Spinner /> : "저장"}
          </Button>
        </div>
      </Field>
      {msg && <Banner tone={msg.tone}>{msg.text}</Banner>}
    </Section>
  );
}

function HeaderSection({ initial }: { initial: string }) {
  const [value, setValue] = useState(initial);
  const [msg, setMsg] = useState<Msg>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    setBusy(true);
    setMsg(null);
    const res = await fetch("/api/settings", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ headerText: value }),
    });
    const data = await res.json();
    setMsg(res.ok ? { tone: "ok", text: "저장했어요. " + REFLECT_NOTE } : { tone: "danger", text: data.error });
    setBusy(false);
  }

  return (
    <Section title="헤더 문구" desc="태블릿 메인 화면 상단에 보이는 안내 문구예요.">
      <Field label="문구 (최대 60자)">
        <Input value={value} onChange={(e) => setValue(e.target.value)} maxLength={60} placeholder="예: 오늘 대한민국의 TOP 콘텐츠" />
      </Field>
      <div className="flex justify-end">
        <Button onClick={save} disabled={busy}>
          {busy ? <Spinner /> : "저장"}
        </Button>
      </div>
      {msg && <Banner tone={msg.tone}>{msg.text}</Banner>}
    </Section>
  );
}

function PinSection() {
  const [pin, setPin] = useState("");
  const [pin2, setPin2] = useState("");
  const [msg, setMsg] = useState<Msg>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    if (pin !== pin2) {
      setMsg({ tone: "danger", text: "두 PIN이 서로 달라요." });
      return;
    }
    setBusy(true);
    setMsg(null);
    const res = await fetch("/api/settings/pin", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pin }),
    });
    const data = await res.json();
    if (res.ok) {
      setMsg({ tone: "ok", text: "PIN을 바꿨어요. " + REFLECT_NOTE });
      setPin("");
      setPin2("");
    } else {
      setMsg({ tone: "danger", text: data.error });
    }
    setBusy(false);
  }

  return (
    <Section title="앱 관리자 PIN" desc="태블릿에서 관리자 화면에 들어갈 때 쓰는 숫자 6자리 PIN이에요.">
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="새 PIN (숫자 6자리)">
          <Input inputMode="numeric" maxLength={6} value={pin} onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))} placeholder="••••••" />
        </Field>
        <Field label="새 PIN 다시 입력">
          <Input inputMode="numeric" maxLength={6} value={pin2} onChange={(e) => setPin2(e.target.value.replace(/\D/g, ""))} placeholder="••••••" />
        </Field>
      </div>
      <div className="flex justify-end">
        <Button onClick={save} disabled={busy || pin.length !== 6}>
          {busy ? <Spinner /> : "PIN 변경"}
        </Button>
      </div>
      {msg && <Banner tone={msg.tone}>{msg.text}</Banner>}
    </Section>
  );
}

function PasswordSection({ highlight }: { highlight: boolean }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [next2, setNext2] = useState("");
  const [msg, setMsg] = useState<Msg>(null);
  const [busy, setBusy] = useState(false);

  async function save() {
    if (next !== next2) {
      setMsg({ tone: "danger", text: "새 비밀번호가 서로 달라요." });
      return;
    }
    setBusy(true);
    setMsg(null);
    const res = await fetch("/api/settings/password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ current, next }),
    });
    const data = await res.json();
    if (res.ok) {
      setMsg({ tone: "ok", text: "비밀번호를 바꿨어요." });
      setCurrent("");
      setNext("");
      setNext2("");
    } else {
      setMsg({ tone: "danger", text: data.error });
    }
    setBusy(false);
  }

  return (
    <div className={highlight ? "rounded-2xl ring-2 ring-warn/50" : ""}>
      <Section title="웹 로그인 비밀번호" desc="이 관리자 페이지에 로그인할 때 쓰는 비밀번호예요. (8자 이상)">
        <Field label="현재 비밀번호">
          <Input type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="새 비밀번호">
            <Input type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} />
          </Field>
          <Field label="새 비밀번호 다시 입력">
            <Input type="password" autoComplete="new-password" value={next2} onChange={(e) => setNext2(e.target.value)} />
          </Field>
        </div>
        <div className="flex justify-end">
          <Button onClick={save} disabled={busy || !current || next.length < 8}>
            {busy ? <Spinner /> : "비밀번호 변경"}
          </Button>
        </div>
        {msg && <Banner tone={msg.tone}>{msg.text}</Banner>}
      </Section>
    </div>
  );
}
