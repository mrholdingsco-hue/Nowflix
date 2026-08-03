import { getSettings } from "@/lib/db";
import { isPasswordDefault } from "@/lib/auth";
import { SettingsForm } from "@/components/SettingsForm";

export const dynamic = "force-dynamic";

export default async function SettingsPage() {
  const settings = await getSettings();
  const passwordIsDefault = await isPasswordDefault();
  return (
    <SettingsForm
      initialIdle={settings.idle_return_seconds}
      initialHeader={settings.header_text ?? ""}
      passwordIsDefault={passwordIsDefault}
    />
  );
}
