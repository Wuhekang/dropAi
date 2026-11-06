package com.dropai.rewrite.service.writing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WritingBranchConcurrencyTest {
    @Test
    void startsEverySectionBeforeWaitingAndPreservesOutlineOrder() {
        int sectionCount = 12;
        CountDownLatch started = new CountDownLatch(sectionCount);
        List<Supplier<Integer>> sections = IntStream.range(0, sectionCount)
                .<Supplier<Integer>>mapToObj(index -> () -> {
                    started.countDown();
                    try {
                        assertTrue(started.await(5, TimeUnit.SECONDS), "所有二级标题应同时启动");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                    return index;
                }).toList();

        assertEquals(IntStream.range(0, sectionCount).boxed().toList(),
                WritingGenerationService.runBranchesConcurrently(sections));
    }
}
