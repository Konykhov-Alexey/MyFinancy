# Debt Tracking Feature — Design Doc

**Date:** 2026-02-27
**Branch:** add-new-functional
**Status:** Approved

---

## Summary

Add debt/credit tracking to the Goals screen as a second tab ("Долги") alongside savings goals ("Накопления"). Users can log debts with interest rates, track repayments, and see overpayment estimates.

---

## Data Model

### New entity: `Debt` (table: `debts`)

| Field | Type | Nullable | Description |
|---|---|---|---|
| `id` | Long | No | PK, auto-generated |
| `name` | String | No | Debt name (e.g., "Ипотека Сбер") |
| `creditorName` | String | Yes | Lender name (e.g., "Сбербанк") |
| `totalAmount` | BigDecimal | No | Original debt amount |
| `paidAmount` | BigDecimal | No | Amount repaid so far (starts at 0) |
| `interestRate` | BigDecimal | Yes | Annual interest rate (%) |
| `minimumPayment` | BigDecimal | Yes | Minimum monthly payment |
| `nextPaymentDate` | LocalDate | Yes | Next required payment date |
| `deadline` | LocalDate | Yes | Target full repayment date |
| `status` | DebtStatus | No | ACTIVE / PAID / CANCELLED |

**Computed values (not stored):**
- `remaining = totalAmount - paidAmount`
- `progress = paidAmount / totalAmount`
- `overpayment` (informational): `remaining * (interestRate/100) * monthsToDeadline/12`
  Only shown when both `interestRate` and `deadline` are set.

### New enum: `DebtStatus`
```
ACTIVE, PAID, CANCELLED
```

---

## Architecture

### Repository: `DebtRepository`
- `findAll(DebtSort)` — HQL query, ordered by sort
- `save(Debt)` — merge + commit
- `delete(Debt)` — get + remove + commit
- Inner enum `DebtSort { BY_REMAINING, BY_NEXT_PAYMENT }`

### Service: `DebtService`
- `create(name, creditorName, totalAmount, interestRate, minimumPayment, nextPaymentDate, deadline)` → `Debt`
- `repay(Debt, amount)` → `Debt`
  Adds to `paidAmount`. Auto-transitions to PAID when `paidAmount >= totalAmount`.
- `cancel(Debt)` → `Debt` (sets CANCELLED, rejects PAID)
- `delete(Debt)` → void

### Controller: `GoalController` (extended)
- Gains a `TabPane` with two tabs: "Накопления" and "Долги"
- Separate `FlowPane` for debts (`debtsPane`)
- `DebtService debtService` field
- `refreshDebts()` method — builds debt cards
- `buildDebtCard(Debt)` — card UI (same 270px `.goal-card` style)
- `openAddDebtDialog()` — dialog for creating new debt
- `openRepayDialog(Debt)` — dialog for logging a repayment
- Sort ComboBox for debts: "По остатку" / "По платежу"
- Add button label changes based on active tab

---

## UI Design

### goals.fxml changes
- Wrap current `FlowPane` in a `Tab` ("Накопления")
- Add new `Tab` ("Долги") with its own `FlowPane`
- Header: button text becomes "Новая цель" / "Новый долг" based on active tab
- Sort ComboBox reflects the active tab's sort options

### Debt card layout (`.goal-card`)
```
[ ACTIVE badge ]         [ creditor name ]
  Debt name
  ─────────────────────────────
  Progress bar (paidAmount / totalAmount)
  Выплачено: X ₽      Осталось: Y ₽
  Прогресс: Z%
  Ставка: 14.5% · Переплата ≈ W ₽  (if applicable)
  Следующий платёж: DD.MM.YYYY  (red if overdue)
  ─────────────────────────────
  [ Внести платёж ]  [ Отменить ]
```

### Dialog: "Новый долг"
Fields: Название*, Кредитор, Сумма долга*, % годовых, Мин. платёж/мес, Дата след. платежа, Дата погашения
(*required)

### Dialog: "Внести платёж"
Shows: remaining amount, minimum payment (hint)
Field: Сумма платежа*

---

## CSS

Reuse existing `.goal-card`, `.goal-progress-bar`, `.goal-status-badge`, `.goal-badge-*` classes.

New classes needed:
- `.debt-creditor` — muted label for creditor name
- `.debt-overpayment` — dim/warning color for overpayment line
- `.debt-next-payment` — similar to `.goal-deadline`
- `.debt-next-payment-overdue` — red, similar to `.goal-deadline-overdue`

---

## Files to Create/Modify

| File | Action |
|---|---|
| `model/enums/DebtStatus.java` | Create |
| `model/entity/Debt.java` | Create |
| `repository/DebtRepository.java` | Create |
| `service/DebtService.java` | Create |
| `controller/GoalController.java` | Modify (add tabs + debt logic) |
| `fxml/goals.fxml` | Modify (add TabPane) |
| `css/styles.css` | Modify (add debt card CSS classes) |
