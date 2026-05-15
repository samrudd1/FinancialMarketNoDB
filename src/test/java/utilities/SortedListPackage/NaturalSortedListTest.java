package utilities.SortedListPackage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NaturalSortedListTest {

    private NaturalSortedList<Integer> list;

    @BeforeEach
    void setUp() {
        list = new NaturalSortedList<>();
    }

    // --- Empty state ---

    @Test
    void newListIsEmpty() {
        assertThat(list.isEmpty()).isTrue();
        assertThat(list.size()).isZero();
    }

    // --- Add ---

    @Test
    void addNullReturnsFalseAndLeavesListUnchanged() {
        boolean result = list.add((Integer) null);
        assertThat(result).isFalse();
        assertThat(list.isEmpty()).isTrue();
    }

    @Test
    void addNonNullReturnsTrueAndIncreasesSize() {
        assertThat(list.add(5)).isTrue();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.isEmpty()).isFalse();
    }

    @Test
    void addedSingleElementIsRetrievableAtIndexZero() {
        list.add(42);
        assertThat(list.get(0)).isEqualTo(42);
    }

    // --- Sorted order ---

    @Test
    void elementsInsertedOutOfOrderComeOutSorted() {
        list.add(30);
        list.add(10);
        list.add(20);
        assertThat(list.get(0)).isEqualTo(10);
        assertThat(list.get(1)).isEqualTo(20);
        assertThat(list.get(2)).isEqualTo(30);
    }

    @Test
    void elementsInsertedInAscendingOrderAreCorrect() {
        list.add(1);
        list.add(2);
        list.add(3);
        assertThat(list).containsExactly(1, 2, 3);
    }

    @Test
    void elementsInsertedInDescendingOrderAreSorted() {
        list.add(3);
        list.add(2);
        list.add(1);
        assertThat(list).containsExactly(1, 2, 3);
    }

    @Test
    void bulkInsertMaintainsSortOrder() {
        int[] values = {50, 3, 99, 1, 77, 22, 44, 88, 11, 66};
        for (int v : values) list.add(v);
        assertThat(list).containsExactlyElementsOf(List.of(1, 3, 11, 22, 44, 50, 66, 77, 88, 99));
    }

    // --- Duplicates ---

    @Test
    void duplicateValuesAreBothStored() {
        list.add(5);
        list.add(5);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0)).isEqualTo(5);
        assertThat(list.get(1)).isEqualTo(5);
    }

    @Test
    void duplicatesArePositionedBetweenSmallerAndLargerElements() {
        list.add(10);
        list.add(5);
        list.add(5);
        list.add(1);
        assertThat(list).containsExactly(1, 5, 5, 10);
    }

    // --- Contains ---

    @Test
    void containsReturnsTrueForPresentElement() {
        list.add(42);
        assertThat(list.contains(42)).isTrue();
    }

    @Test
    void containsReturnsFalseForAbsentElement() {
        list.add(42);
        assertThat(list.contains(99)).isFalse();
    }

    @Test
    void containsReturnsFalseOnEmptyList() {
        assertThat(list.contains(1)).isFalse();
    }

    @Test
    void containsReturnsFalseForNull() {
        list.add(1);
        assertThat(list.contains(null)).isFalse();
    }

    // --- Remove by value ---

    @Test
    void removeByValueReturnsTrueAndReducesSize() {
        list.add(10);
        list.add(20);
        assertThat(list.remove(Integer.valueOf(10))).isTrue();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0)).isEqualTo(20);
    }

    @Test
    void removeAbsentValueReturnsFalseAndLeavesListUnchanged() {
        list.add(10);
        assertThat(list.remove(Integer.valueOf(99))).isFalse();
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void removeOneOfDuplicatesLeavesOtherIntact() {
        list.add(5);
        list.add(5);
        list.remove(Integer.valueOf(5));
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0)).isEqualTo(5);
    }

    // --- Remove by index ---

    @Test
    void removeByIndexZeroRemovesSmallestElement() {
        list.add(10);
        list.add(20);
        list.add(30);
        assertThat(list.remove(0)).isEqualTo(10);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0)).isEqualTo(20);
    }

    @Test
    void removeByLastIndexRemovesLargestElement() {
        list.add(10);
        list.add(20);
        list.add(30);
        assertThat(list.remove(2)).isEqualTo(30);
        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void removeByInvalidIndexThrowsIllegalArgumentException() {
        list.add(1);
        assertThatThrownBy(() -> list.remove(5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeByNegativeIndexThrowsIllegalArgumentException() {
        list.add(1);
        assertThatThrownBy(() -> list.remove(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Get ---

    @Test
    void getOnEmptyListThrowsIllegalArgumentException() {
        assertThatThrownBy(() -> list.get(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getOutOfBoundsThrowsIllegalArgumentException() {
        list.add(1);
        assertThatThrownBy(() -> list.get(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Clear ---

    @Test
    void clearEmptiesTheList() {
        list.add(1);
        list.add(2);
        list.clear();
        assertThat(list.isEmpty()).isTrue();
        assertThat(list.size()).isZero();
    }

    @Test
    void clearOnEmptyListDoesNotThrow() {
        assertThatCode(() -> list.clear()).doesNotThrowAnyException();
    }

    @Test
    void elementsCanBeAddedAfterClear() {
        list.add(1);
        list.clear();
        list.add(99);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0)).isEqualTo(99);
    }

    // --- Iterator ---

    @Test
    void iteratorTraversesInSortedAscendingOrder() {
        list.add(30);
        list.add(10);
        list.add(20);
        Iterator<Integer> it = list.iterator();
        assertThat(it.next()).isEqualTo(10);
        assertThat(it.next()).isEqualTo(20);
        assertThat(it.next()).isEqualTo(30);
        assertThat(it.hasNext()).isFalse();
    }

    @Test
    void iteratorHasNextReturnsFalseOnEmptyList() {
        assertThat(list.iterator().hasNext()).isFalse();
    }

    @Test
    void iteratorNextOnExhaustedIteratorThrowsNoSuchElementException() {
        list.add(1);
        Iterator<Integer> it = list.iterator();
        it.next();
        assertThatThrownBy(it::next).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void iteratorRemoveDeletesLastReturnedElement() {
        list.add(10);
        list.add(20);
        list.add(30);
        Iterator<Integer> it = list.iterator();
        it.next(); // positions on 10
        it.remove();
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.contains(10)).isFalse();
    }

    @Test
    void externalAddAfterIteratorCreatedCausesConcurrentModificationException() {
        list.add(1);
        list.add(2);
        Iterator<Integer> it = list.iterator();
        it.next();
        list.add(3); // structural modification outside iterator
        assertThatThrownBy(it::next)
                .isInstanceOf(ConcurrentModificationException.class);
    }

    @Test
    void externalRemoveAfterIteratorCreatedCausesConcurrentModificationException() {
        list.add(1);
        list.add(2);
        Iterator<Integer> it = list.iterator();
        it.next();
        list.remove(Integer.valueOf(2));
        assertThatThrownBy(it::next)
                .isInstanceOf(ConcurrentModificationException.class);
    }

    // --- toArray ---

    @Test
    void toArrayReturnsSortedElements() {
        list.add(3);
        list.add(1);
        list.add(2);
        assertThat(list.toArray()).containsExactly(1, 2, 3);
    }

    @Test
    void toArrayOnEmptyListReturnsEmptyArray() {
        assertThat(list.toArray()).isEmpty();
    }

    // --- AVL balance invariant ---
    // minBalanceFactor / maxBalanceFactor are package-private — accessible because
    // this test is in the same package (utilities.SortedListPackage).

    @Test
    void avlTreeRemainsBalancedAfterManyAscendingInserts() {
        for (int i = 1; i <= 100; i++) list.add(i);
        assertThat(list.minBalanceFactor()).isGreaterThanOrEqualTo(-1);
        assertThat(list.maxBalanceFactor()).isLessThanOrEqualTo(1);
        assertThat(list.size()).isEqualTo(100);
    }

    @Test
    void avlTreeRemainsBalancedAfterManyDescendingInserts() {
        for (int i = 100; i >= 1; i--) list.add(i);
        assertThat(list.minBalanceFactor()).isGreaterThanOrEqualTo(-1);
        assertThat(list.maxBalanceFactor()).isLessThanOrEqualTo(1);
    }

    @Test
    void avlTreeRemainsBalancedAfterMixedInsertsAndRemoves() {
        for (int i = 1; i <= 50; i++) list.add(i);
        for (int i = 1; i <= 25; i++) list.remove(Integer.valueOf(i));
        for (int i = 51; i <= 75; i++) list.add(i);
        assertThat(list.minBalanceFactor()).isGreaterThanOrEqualTo(-1);
        assertThat(list.maxBalanceFactor()).isLessThanOrEqualTo(1);
        assertThat(list.size()).isEqualTo(50); // 25 remaining + 25 new
    }
}
