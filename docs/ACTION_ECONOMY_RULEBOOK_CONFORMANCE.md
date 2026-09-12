# PTU action-economy conformance

`ActionBudget` owns the base action resources used by authoritative battle
controllers and legal-action generation. This contract is tested directly from
the PTU 1.05/Kairos action economy and is intentionally independent of the
pinned Python oracle while that oracle retains the known bucket-model
divergence tracked by issue #359.

The implemented resource model is:

- one base Standard, Shift, and Swift Action per turn;
- Full consumes the base Standard and Shift together or fails without mutation;
- after the base Swift is spent, Standard may convert to one additional Swift;
- after the base Shift is spent, Standard may convert to one additional Shift;
- Standard-to-Shift cannot pay for a second movement after the regular Shift
  was used for movement, but it can pay for a non-movement Shift effect;
- if the regular Shift paid for a non-movement effect, Standard may still
  convert to a movement Shift;
- Free Actions are not numerically capped by `ActionBudget`;
- named extra-action grants remain separate resources and do not make an extra
  Standard or Shift satisfy the two base resources required by Full.

Callers that move a combatant must use `consumeMovement`. Non-movement Shift
effects use `consume(ActionType.SHIFT, detail)`. Legal movement enumeration uses
`hasCapacity(ActionType.SHIFT, true)` so AI and external controllers receive the
same restriction that execution enforces.

Trigger, Priority, and Interrupt lifecycle restrictions are deliberately not
modeled as ordinary action buckets. Their timing windows and once-per-trigger
semantics remain the responsibility of the corresponding authoritative battle
controllers.
