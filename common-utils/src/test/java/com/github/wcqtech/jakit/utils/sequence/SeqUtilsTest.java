package com.github.wcqtech.jakit.utils.sequence;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeqUtilsTest {

    private static final class Item {
        private Integer seq;
        private String label;

        void setSeq(Integer seq) {
            this.seq = seq;
        }

        void setLabel(String label) {
            this.label = label;
        }
    }

    private static List<Integer> seqValues(List<Item> items) {
        List<Integer> values = new ArrayList<>();
        for (Item item : items) {
            values.add(item.seq);
        }
        return values;
    }

    private static List<String> labels(List<Item> items) {
        List<String> values = new ArrayList<>();
        for (Item item : items) {
            values.add(item.label);
        }
        return values;
    }

    @Test
    void assignsSequenceFromStartWithStep() {
        List<Item> items = List.of(new Item(), new Item(), new Item());

        SeqUtils.sequence(items, Item::setSeq, seq -> seq, 10, 2);

        assertEquals(List.of(10, 12, 14), seqValues(items));
    }

    @Test
    void assignsDescendingSequenceWithNegativeStep() {
        List<Item> items = List.of(new Item(), new Item(), new Item());

        SeqUtils.sequence(items, Item::setSeq, 3, -1);

        assertEquals(List.of(3, 2, 1), seqValues(items));
    }

    @Test
    void defaultsToStartOneAndStepOne() {
        List<Item> items = List.of(new Item(), new Item(), new Item());

        SeqUtils.sequence(items, Item::setSeq);

        assertEquals(List.of(1, 2, 3), seqValues(items));
    }

    @Test
    void convertsSequenceBeforeAssignment() {
        List<Item> items = List.of(new Item(), new Item());

        SeqUtils.sequence(items, Item::setLabel, seq -> "seq-" + seq, 5, 5);

        assertEquals(List.of("seq-5", "seq-10"), labels(items));
    }

    @Test
    void convertsSequenceWithDefaultStartAndStep() {
        List<Item> items = List.of(new Item(), new Item());

        SeqUtils.sequence(items, Item::setLabel, seq -> "seq-" + seq);

        assertEquals(List.of("seq-1", "seq-2"), labels(items));
    }

    @Test
    void invokesVisitorWithDefaultStartAndStep() {
        List<Item> items = List.of(new Item(), new Item());
        List<Item> visited = new ArrayList<>();

        SeqUtils.sequence(items, Item::setSeq, seq -> seq, visited::add);

        assertEquals(List.of(1, 2), seqValues(items));
        assertEquals(items, visited);
    }

    @Test
    void invokesVisitorForEachElement() {
        List<Item> items = List.of(new Item(), new Item());
        List<Item> visited = new ArrayList<>();

        SeqUtils.sequence(items, Item::setSeq, seq -> seq, 1, 1, visited::add);

        assertEquals(items, visited);
    }

    @Test
    void visitorSeesAssignedSequence() {
        List<Item> items = List.of(new Item());
        List<Integer> observed = new ArrayList<>();

        SeqUtils.sequence(items, Item::setSeq, 7, 1, item -> observed.add(item.seq));

        assertEquals(List.of(7), observed);
    }

    @Test
    void emptyCollectionIsANoOp() {
        List<Item> items = List.of();

        assertDoesNotThrow(() -> SeqUtils.sequence(items, Item::setSeq));
    }

    @Test
    void rejectsNullCollection() {
        assertThrows(NullPointerException.class, () -> SeqUtils.sequence(null, Item::setSeq));
    }

    @Test
    void rejectsNullCallbacks() {
        List<Item> items = List.of(new Item());

        assertThrows(NullPointerException.class, () -> SeqUtils.sequence(items, null));
        assertThrows(NullPointerException.class,
                () -> SeqUtils.sequence(items, Item::setSeq, (Function<Integer, Integer>) null, 1, 1));
        assertThrows(NullPointerException.class,
                () -> SeqUtils.sequence(items, Item::setSeq, seq -> seq, 1, 1, null));
        assertThrows(NullPointerException.class,
                () -> SeqUtils.sequence(items, Item::setSeq, seq -> seq, null));
    }
}
