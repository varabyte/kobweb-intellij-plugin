package com.varabyte.kobweb.intellij.util.kotlin

import com.varabyte.truthish.assertThat
import org.junit.Test

class KotlinLangUtilsTest {
    @Test
    fun isPackageSegment() {
        assertThat("example".isPackageSegment()).isTrue()
        assertThat("my_site".isPackageSegment()).isTrue()
        assertThat("site2".isPackageSegment()).isTrue()
        assertThat("data".isPackageSegment()).isTrue() // Soft keyword

        assertThat("".isPackageSegment()).isFalse()
        assertThat("2048".isPackageSegment()).isFalse()
        assertThat("my-site".isPackageSegment()).isFalse()
        assertThat("object".isPackageSegment()).isFalse()
        assertThat("fun".isPackageSegment()).isFalse()
        assertThat("new".isPackageSegment()).isFalse() // Java keyword
        assertThat("int".isPackageSegment()).isFalse() // Java keyword
        assertThat("null".isPackageSegment()).isFalse() // Java and Kotlin literal
        assertThat("`my site`".isPackageSegment()).isFalse()
    }

    @Test
    fun toPackageSegment() {
        assertThat("untitled".toPackageSegment()).isEqualTo("untitled")
        assertThat("My Site".toPackageSegment()).isEqualTo("mysite")
        assertThat("my-site".toPackageSegment()).isEqualTo("mysite")
        assertThat("my_site".toPackageSegment()).isEqualTo("mysite")
        assertThat("2048".toPackageSegment()).isEqualTo("_2048")
        assertThat("3D Site".toPackageSegment()).isEqualTo("_3dsite")
        assertThat("Object".toPackageSegment()).isEqualTo("object_")
        assertThat("New".toPackageSegment()).isEqualTo("new_")
        assertThat("data".toPackageSegment()).isEqualTo("data")

        assertThat("!!!".toPackageSegment()).isNull()
        assertThat("".toPackageSegment()).isNull()
    }
}
