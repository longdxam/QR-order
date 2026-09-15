"use client";

import { useMemo, useState } from "react";

import type { MenuItem } from "@/lib/api";
import { addCartLine } from "@/lib/cart";

const ALLERGEN_LABELS: Record<string, string> = {
  MILK: "Sữa",
  PEANUT: "Đậu phộng",
  GLUTEN: "Gluten",
  SOY: "Đậu nành",
  EGG: "Trứng",
  NUTS: "Hạt",
};

type Props = {
  sessionId: string;
  addedBy?: string;
  item: MenuItem;
  onClose: () => void;
  onAdded: () => void;
};

export function AddToCartDialog({ sessionId, addedBy, item, onClose, onAdded }: Props) {
  const firstAvailableVariant = item.variants.find((variant) => variant.available !== false);
  const [variantId, setVariantId] = useState(firstAvailableVariant?.id ?? "");
  const [selectedOptions, setSelectedOptions] = useState<Record<string, string[]>>({});
  const [quantity, setQuantity] = useState(1);
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const variant = item.variants.find((candidate) => candidate.id === variantId);
  const selectedOptionDetails = useMemo(() => {
    return (item.optionGroups ?? []).flatMap((group) =>
      group.options.filter((option) => selectedOptions[group.id]?.includes(option.id)),
    );
  }, [item.optionGroups, selectedOptions]);
  const estimatedUnitAmount = (variant?.price.amount ?? item.basePrice.amount)
    + selectedOptionDetails.reduce((sum, option) => sum + option.surcharge.amount, 0);

  function toggleOption(groupId: string, optionId: string, single: boolean, maxSelect?: number) {
    setError(null);
    setSelectedOptions((current) => {
      const selected = current[groupId] ?? [];
      if (single) return { ...current, [groupId]: selected.includes(optionId) ? [] : [optionId] };
      if (selected.includes(optionId)) {
        return { ...current, [groupId]: selected.filter((id) => id !== optionId) };
      }
      if (maxSelect !== undefined && selected.length >= maxSelect) return current;
      return { ...current, [groupId]: [...selected, optionId] };
    });
  }

  function validate(): string | null {
    if (!item.available || !variant || variant.available === false) return "Món hoặc kích cỡ này đang tạm hết.";
    for (const group of item.optionGroups ?? []) {
      const count = selectedOptions[group.id]?.length ?? 0;
      const minimum = group.required ? Math.max(1, group.minSelect ?? 0) : (group.minSelect ?? 0);
      if (count < minimum) return `Vui lòng chọn ít nhất ${minimum} lựa chọn trong “${group.name}”.`;
      if (group.maxSelect !== undefined && count > group.maxSelect) {
        return `Chỉ được chọn tối đa ${group.maxSelect} lựa chọn trong “${group.name}”.`;
      }
    }
    return null;
  }

  async function add() {
    const validationError = validate();
    if (validationError) {
      setError(validationError);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await addCartLine(sessionId, {
        menuItemId: item.id,
        menuItemName: item.name,
        variantId: variant!.id,
        variantName: variant!.name,
        optionIds: selectedOptionDetails.map((option) => option.id),
        optionNames: selectedOptionDetails.map((option) => option.name),
        quantity,
        note: note.trim() || undefined,
        addedBy,
        displayUnitAmount: estimatedUnitAmount,
      });
      onAdded();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Không lưu được giỏ hàng.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      role="presentation"
      onClick={onClose}
      style={{ position: "fixed", inset: 0, zIndex: 10, background: "rgba(0,0,0,0.45)", display: "flex", alignItems: "flex-end" }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="cart-dialog-title"
        onClick={(event) => event.stopPropagation()}
        style={{ background: "light-dark(#fff,#222)", width: "100%", padding: "1.25rem", borderRadius: "1rem 1rem 0 0", maxHeight: "88dvh", overflowY: "auto" }}
      >
        <h2 id="cart-dialog-title" style={{ marginTop: 0 }}>{item.name}</h2>
        {item.description && <p style={{ color: "#666" }}>{item.description}</p>}
        {item.allergens && item.allergens.length > 0 && (
          <p>⚠ Dị ứng: {item.allergens.map((allergen) => ALLERGEN_LABELS[allergen] ?? allergen).join(", ")}</p>
        )}

        <fieldset style={fieldsetStyle}>
          <legend style={legendStyle}>Kích cỡ</legend>
          {item.variants.map((candidate) => (
            <label key={candidate.id} style={choiceStyle(candidate.available === false)}>
              <span>
                <input
                  type="radio"
                  name="variant"
                  value={candidate.id}
                  checked={variantId === candidate.id}
                  disabled={candidate.available === false}
                  onChange={() => setVariantId(candidate.id)}
                />{" "}{candidate.name}
              </span>
              <span>{formatMoney(candidate.price.amount)}</span>
            </label>
          ))}
        </fieldset>

        {item.optionGroups?.map((group) => {
          const minimum = group.required ? Math.max(1, group.minSelect ?? 0) : (group.minSelect ?? 0);
          return (
            <fieldset key={group.id} style={fieldsetStyle}>
              <legend style={legendStyle}>
                {group.name} {minimum > 0 ? `(chọn ít nhất ${minimum})` : "(không bắt buộc)"}
              </legend>
              {group.options.map((option) => (
                <label key={option.id} style={choiceStyle(option.available === false)}>
                  <span>
                    <input
                      type={group.selection === "SINGLE" ? "radio" : "checkbox"}
                      name={`option-${group.id}`}
                      checked={selectedOptions[group.id]?.includes(option.id) ?? false}
                      disabled={option.available === false}
                      onChange={() => toggleOption(group.id, option.id, group.selection === "SINGLE", group.maxSelect)}
                    />{" "}{option.name}
                  </span>
                  <span>{option.surcharge.amount > 0 ? `+${formatMoney(option.surcharge.amount)}` : ""}</span>
                </label>
              ))}
            </fieldset>
          );
        })}

        <label style={{ display: "block", marginTop: "1rem" }}>
          Ghi chú cho món ({note.length}/200)
          <textarea
            value={note}
            maxLength={200}
            rows={3}
            onChange={(event) => setNote(event.target.value)}
            placeholder="Ví dụ: ít đá, không ống hút…"
            style={{ display: "block", width: "100%", marginTop: "0.35rem", padding: "0.65rem", font: "inherit" }}
          />
        </label>

        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: "1rem" }}>
          <label>
            Số lượng{" "}
            <input
              aria-label="Số lượng"
              type="number"
              min={1}
              max={20}
              value={quantity}
              onChange={(event) => setQuantity(Math.max(1, Math.min(20, Number(event.target.value) || 1)))}
              style={{ width: "4rem", padding: "0.4rem" }}
            />
          </label>
          <strong>{formatMoney(estimatedUnitAmount * quantity)}</strong>
        </div>

        {error && <p role="alert" style={{ color: "#b00020" }}>{error}</p>}
        <div style={{ display: "flex", gap: "0.5rem", marginTop: "1rem" }}>
          <button type="button" onClick={onClose} style={secondaryButtonStyle}>Đóng</button>
          <button type="button" disabled={saving || !item.available} onClick={() => void add()} style={primaryButtonStyle}>
            {saving ? "Đang lưu…" : "Thêm vào giỏ"}
          </button>
        </div>
      </section>
    </div>
  );
}

function choiceStyle(disabled: boolean): React.CSSProperties {
  return { display: "flex", justifyContent: "space-between", padding: "0.45rem 0", opacity: disabled ? 0.45 : 1 };
}

function formatMoney(amount: number): string {
  return `${amount.toLocaleString("vi-VN")}đ`;
}

const fieldsetStyle: React.CSSProperties = { border: 0, padding: 0, margin: "1rem 0 0" };
const legendStyle: React.CSSProperties = { fontWeight: 650, marginBottom: "0.25rem" };
const primaryButtonStyle: React.CSSProperties = { flex: 1, padding: "0.75rem", border: 0, borderRadius: "0.5rem", background: "#087f5b", color: "#fff", fontWeight: 650 };
const secondaryButtonStyle: React.CSSProperties = { padding: "0.75rem 1rem", border: "1px solid #aaa", borderRadius: "0.5rem", background: "transparent" };
