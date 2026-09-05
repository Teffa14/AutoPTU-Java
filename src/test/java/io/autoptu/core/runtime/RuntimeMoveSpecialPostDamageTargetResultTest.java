package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeMoveSpecialPostDamageTargetResultTest {
    @Test
    void convertsResolvedPostDamageStateIntoTheCanonicalTargetTransport() {
        AppliedActionResult actionResult = new AppliedActionResult(List.of());
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("hit", true);
        snapshot.put("marker", "post_damage");
        RuntimeMoveSpecialPostDamageApplication.Result resolved =
                new RuntimeMoveSpecialPostDamageApplication.Result(actionResult, snapshot, 17);

        snapshot.put("marker", "mutated-after-resolution");
        MoveSpecialTargetResult transported = resolved.targetResult();

        assertSame(actionResult, transported.actionResult());
        assertEquals(17, transported.damageDealt());
        assertEquals(Map.of("hit", true, "marker", "post_damage"), transported.resultSnapshot());
        assertThrows(UnsupportedOperationException.class,
                () -> transported.resultSnapshot().put("mutate", true));
    }
}
