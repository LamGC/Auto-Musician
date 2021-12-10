package net.lamgc.automusician

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UtilsTest {

    @Test
    fun `test nextString in Random`() {
        val length = Random.nextInt(32)
        val string = Random.nextString(length)
        assertEquals(length, string.length)

        for (char in string) {
            assert(char in 'a'..'z' || char in 'A'..'Z' || char in '0'..'9') {
                "Contains unexpected characters: `$char`"
            }
        }
    }

    @Test
    fun `test randomElement in List`() {
        val list = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
        for (i in 1..50) {
            val element = list.randomElement()
            assertContains(list, element, "An element that is not contained was taken out: `$element`")
        }
    }

}

class MultiValueMapTest {

    @Test
    fun `normal test`() {
        val mvMap = MultiValueMap<Int, Int>()
        for (i in 1..10) {
            for (i2 in 1..i) {
                // Do not use symbolic operations.
                @Suppress("ReplacePutWithAssignment")
                mvMap.put(i, i2)
            }
        }

        for (i in 1..10) {
            for (i2 in 1..i) {
                assert(mvMap.containsValue(i, i2)) {
                    "Missing value: $i2"
                }
            }
            mvMap.clear(i)
            assert(mvMap.isEmpty(i)) {
                "`isEmpty` returns false after the `clear` operation " +
                        "is performed on the list to which the key belongs."
            }
        }

        val noExistKey = Random.nextInt(11, 20)
        assertFalse(
            mvMap.contains(noExistKey),
            "Contains returns true for keys that do not exist"
        )
        assertFalse(
            mvMap.containsValue(noExistKey, Random.nextInt()),
            "Contains returns true for keys that do not exist"
        )

    }

}
