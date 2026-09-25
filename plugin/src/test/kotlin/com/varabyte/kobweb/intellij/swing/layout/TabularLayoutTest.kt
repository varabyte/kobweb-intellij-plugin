package com.varabyte.kobweb.intellij.swing.layout

import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.ui.scale.JBUIScale.setUserScaleFactorForTest
import com.varabyte.kobweb.intellij.util.junit.rules.EdtRule
import com.varabyte.truthish.assertThat
import com.varabyte.truthish.assertThrows
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JPanel

class TabularLayoutTest {
    @get:Rule
    val edtRule = EdtRule()

    @Test
    fun columnCountMatchesLayoutDefinition() {
        val layout = TabularLayout("Fit-,*,123px")
        assertThat(layout.numColumns).isEqualTo(3)
    }

    @Test
    fun layoutThrowsExceptionIfComponentAddedWithoutConstraint() {
        val panel = JPanel(TabularLayout("*"))
        val row1 = JPanel()
        val row2 = JPanel()

        panel.add(row1, TabularLayout.Constraint(0, 0))
        assertThrows<Exception> {
            panel.add(row2)
        }
    }

    @Test
    fun layoutDoesNotThrowsExceptionWithPreferredSizeCalledEarlier() {
        val panel = JPanel(TabularLayout("*,*,*"))
        val panel2 = JPanel(TabularLayout("*,*,*"))
        val row1 = JPanel()
        val row2 = JPanel()
        panel2.add(row1, TabularLayout.Constraint(0, 0))
        panel2.add(row2, TabularLayout.Constraint(0, 1))
        (panel.layout as TabularLayout).preferredLayoutSize(panel2)
        panel.add(row1, TabularLayout.Constraint(0, 0))
        panel.add(row2, TabularLayout.Constraint(0, 0))
    }

    @Test
    fun fitPreferredWidthUsesComponentPreferredSizeNotMinimumSize() {
        val panel = JPanel(TabularLayout("Fit"))

        val col0 = Box.createHorizontalStrut(20)
        col0.setMinimumSize(Dimension(10, 10))
        col0.setPreferredSize(Dimension(20, 20))

        panel.add(col0, TabularLayout.Constraint(0, 0))

        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(20)
    }

    @Test
    fun fitPreferredWorksWithFitSize() {
        val panel = JPanel(TabularLayout("Fit,Fit-"))

        val col0 = Box.createHorizontalStrut(20)
        col0.setMinimumSize(Dimension(10, 10))
        col0.setPreferredSize(Dimension(20, 20))
        val col1 = Box.createHorizontalStrut(30)
        col1.setMinimumSize(Dimension(30, 30))
        col1.setPreferredSize(Dimension(100, 100))

        panel.add(col0, TabularLayout.Constraint(0, 0))
        panel.add(col1, TabularLayout.Constraint(0, 1))

        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(50)
    }

    @Test
    fun fitPreferredWorksAsRowSizing() {
        val panel = JPanel(TabularLayout("Fit-,Fit", "Fit"))

        val row0 = Box.createHorizontalStrut(20)
        row0.setMinimumSize(Dimension(10, 10))
        row0.setPreferredSize(Dimension(20, 20))
        val row1 = Box.createHorizontalStrut(30)
        row1.setMinimumSize(Dimension(30, 30))
        row1.setPreferredSize(Dimension(100, 100))

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))

        mockPackPanel(panel)

        assertThat(panel.getHeight()).isEqualTo(50)
    }

    @Test
    fun minimumWidthCalculationUsesFitValues() {
        val panel = JPanel(TabularLayout("Fit-,Fit-"))

        val col0 = Box.createHorizontalStrut(80)
        val col1 = Box.createHorizontalStrut(20)

        panel.add(col0, TabularLayout.Constraint(0, 0))
        panel.add(col1, TabularLayout.Constraint(0, 1))

        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(100)
    }

    @Test
    fun minimumWidthCalculationFixedValuesOverrideComponentSizes() {
        val panel = JPanel(TabularLayout("100px,50px"))

        val col0 = Box.createHorizontalStrut(90)
        val col1 = Box.createHorizontalStrut(90)

        panel.add(col0, TabularLayout.Constraint(0, 0))
        panel.add(col1, TabularLayout.Constraint(0, 1))

        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(150)
    }

    @Test
    fun fitWidthChoosesLargestValueAcrossMultipleRows() {
        val panel = JPanel(TabularLayout("Fit-,Fit-"))

        val row0col0 = Box.createHorizontalStrut(100)
        val row1col0 = Box.createHorizontalStrut(300)
        val row3col0 = Box.createHorizontalStrut(200)

        val row0col1 = Box.createHorizontalStrut(500)
        val row2col1 = Box.createHorizontalStrut(400)
        val row4col1 = Box.createHorizontalStrut(100)

        panel.add(row0col0, TabularLayout.Constraint(0, 0))
        panel.add(row1col0, TabularLayout.Constraint(1, 0))
        panel.add(row3col0, TabularLayout.Constraint(3, 0))

        panel.add(row0col1, TabularLayout.Constraint(0, 1))
        panel.add(row2col1, TabularLayout.Constraint(2, 1))
        panel.add(row4col1, TabularLayout.Constraint(4, 1))

        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(800)

        assertThat(row0col0.getWidth()).isEqualTo(300)
        assertThat(row1col0.getWidth()).isEqualTo(300)
        assertThat(row3col0.getWidth()).isEqualTo(300)

        assertThat(row0col1.getWidth()).isEqualTo(500)
        assertThat(row2col1.getWidth()).isEqualTo(500)
        assertThat(row4col1.getWidth()).isEqualTo(500)
    }

    @Test
    fun fitMinusUsesMinimumAndDefaultIsFitPreferred() {
        val panel = JPanel(TabularLayout("Fit-"))

        val cell = JPanel()
        cell.minimumSize = Dimension(5, 10)
        cell.preferredSize = Dimension(15, 25)

        panel.add(cell, TabularLayout.Constraint(0, 0))
        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(5)
        assertThat(panel.getHeight()).isEqualTo(10)
    }

    @Test
    fun proportionalColumnsTakeRemainingSpace() {
        val panel = JPanel(TabularLayout("100px,Fit-,3*,*,50px"))
        panel.preferredSize = Dimension(300, 20)

        // Col 1 = 100, Col 5 = 50
        // Col 2 = Fit to size 50
        // Total width is 300
        // Leftover space is 100 pixels
        val col0: Component = JPanel()
        val col1 = Box.createHorizontalStrut(50)
        val col2: Component = JPanel()
        val col3: Component = JPanel()
        val col4: Component = JPanel()

        panel.add(col0, TabularLayout.Constraint(0, 0))
        panel.add(col1, TabularLayout.Constraint(0, 1))
        panel.add(col2, TabularLayout.Constraint(0, 2))
        panel.add(col3, TabularLayout.Constraint(0, 3))
        panel.add(col4, TabularLayout.Constraint(0, 4))

        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(300)

        assertThat(col0.getWidth()).isEqualTo(100)
        assertThat(col1.getWidth()).isEqualTo(50)
        assertThat(col2.getWidth()).isEqualTo(75)
        assertThat(col3.getWidth()).isEqualTo(25)
        assertThat(col4.getWidth()).isEqualTo(50)
    }

    @Test
    fun preferredSizeCalculationMakesRoomForProportionalColumns() {
        val panel = JPanel(TabularLayout("*,2*,3*,4*"))

        // Col 0 - 10%
        // Col 1 - 20%
        // Col 2 - 30%
        // Col 3 - 40%
        val col0 = Box.createHorizontalStrut(40) // Needs overall width to be 400
        val col1 = Box.createHorizontalStrut(50) // Needs overall width to be 250
        val col2 = Box.createHorizontalStrut(30) // Needs overall width to be 100
        val col3 = Box.createHorizontalStrut(80) // Needs overall width to be 200

        panel.add(col0, TabularLayout.Constraint(0, 0))
        panel.add(col1, TabularLayout.Constraint(0, 1))
        panel.add(col2, TabularLayout.Constraint(0, 2))
        panel.add(col3, TabularLayout.Constraint(0, 3))

        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(400)
        assertThat(col0.getWidth()).isEqualTo(40)
        assertThat(col1.getWidth()).isEqualTo(80)
        assertThat(col2.getWidth()).isEqualTo(120)
        assertThat(col3.getWidth()).isEqualTo(160)
    }

    @Test
    fun minimumSizeCalculationCollapsesProportionalColumns() {
        val panel = JPanel(TabularLayout("10px,990*,*,20px,3*"))

        mockPackPanel(panel)

        assertThat(panel.getMinimumSize().getWidth()).isWithin(0.1).of(30.0)
    }

    @Test
    fun heightCalculationSkipsEmptyRows() {
        val panel = JPanel(TabularLayout("100px"))

        val row0 = Box.createVerticalStrut(20)
        val row2 = Box.createVerticalStrut(50)

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row2, TabularLayout.Constraint(2, 0))

        mockPackPanel(panel)

        assertThat(panel.getHeight()).isEqualTo(70)

        assertThat(row0.getHeight()).isEqualTo(20)
        assertThat(row2.getY()).isEqualTo(20)
        assertThat(row2.getHeight()).isEqualTo(50)
    }

    @Test
    fun heightCalculationIncludesVgapAndSkipsEmptyRows() {
        val panel = JPanel(TabularLayout("100px").setVGap(20))

        val row2 = Box.createVerticalStrut(20)
        val row4 = Box.createVerticalStrut(40)
        val row6 = Box.createVerticalStrut(60)

        panel.add(row2, TabularLayout.Constraint(2, 0))
        panel.add(row4, TabularLayout.Constraint(4, 0))
        panel.add(row6, TabularLayout.Constraint(6, 0))

        mockPackPanel(panel)

        assertThat(panel.getHeight()).isEqualTo(120 + 40) // 120 from struts, 40 from inner gaps

        assertThat(row2.getY()).isEqualTo(0)
        assertThat(row2.getHeight()).isEqualTo(20)
        assertThat(row4.getY()).isEqualTo(40)
        assertThat(row4.getHeight()).isEqualTo(40)
        assertThat(row6.getY()).isEqualTo(100)
        assertThat(row6.getHeight()).isEqualTo(60)
    }

    @Test
    fun heightCalculationSkipsInvisibleRows() {
        val panel = JPanel(TabularLayout("100px"))

        val row0 = Box.createVerticalStrut(20)
        val row1 = Box.createVerticalStrut(50)
        val row2 = Box.createVerticalStrut(20)

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))
        panel.add(row2, TabularLayout.Constraint(2, 0))

        row1.isVisible = false
        mockPackPanel(panel)
        assertThat(panel.getHeight()).isEqualTo(40)

        row1.isVisible = true
        mockPackPanel(panel)
        assertThat(panel.getHeight()).isEqualTo(90)
    }

    @Test
    fun proportionalLayoutCollapsesIfAllContentsAreInvisible() {
        val panel = JPanel(TabularLayout("Fit-"))

        val row0 = Box.createVerticalStrut(20)
        val row1 = Box.createVerticalStrut(50)
        val row2 = Box.createVerticalStrut(20)

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))
        panel.add(row2, TabularLayout.Constraint(2, 0))

        row0.isVisible = false
        row1.isVisible = false
        row2.isVisible = false
        mockPackPanel(panel)
        assertThat(panel.getHeight()).isEqualTo(0)
        assertThat(panel.getWidth()).isEqualTo(0)
    }

    @Test
    fun layoutTakesInsetsIntoAccount() {
        val panel = JPanel(TabularLayout("*"))
        panel.preferredSize = Dimension(300, 30)

        val cell: Component = JPanel()
        panel.add(cell, TabularLayout.Constraint(0, 0))

        val top = 1
        val left = 2
        val bottom = 3
        val right = 4
        panel.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right))

        mockPackPanel(panel)

        assertThat(panel.getWidth()).isEqualTo(300)
        assertThat(panel.getHeight()).isEqualTo(30)
        assertThat(cell.getWidth()).isEqualTo(300 - left - right)
    }

    @Test
    fun cellsCanSpanAcrossMultipleColumns() {
        val panel = JPanel(TabularLayout("Fit-,Fit-"))

        val row0col0 = Box.createHorizontalStrut(20)
        val row0col1 = Box.createHorizontalStrut(50)
        val row1 = JPanel()
        val row2col0 = Box.createHorizontalStrut(10)
        val row2col1 = Box.createHorizontalStrut(100)

        panel.add(row0col0, TabularLayout.Constraint(0, 0))
        panel.add(row0col1, TabularLayout.Constraint(0, 1))
        panel.add(row1, TabularLayout.Constraint(1, 0, 2))
        panel.add(row2col0, TabularLayout.Constraint(2, 0))
        panel.add(row2col1, TabularLayout.Constraint(2, 1))

        mockPackPanel(panel)
        assertThat(row1.getWidth()).isEqualTo(120)
    }

    @Test
    fun columnSpanMustBeWithinBounds() {
        val panel = JPanel(TabularLayout("Fit-,Fit-"))
        val row = JPanel()

        assertThrows<IllegalArgumentException> {
            panel.add(row, TabularLayout.Constraint(0, 0, 3))
        }
    }

    @Test
    fun columnSpanMustBeGreaterThanZero() {
        val panel = JPanel(TabularLayout("Fit-,Fit-"))
        val row = JPanel()

        assertThrows<IllegalArgumentException> {
            panel.add(row, TabularLayout.Constraint(0, 0, 0))
        }
    }

    @Test
    fun rowsCanBeConfiguredWithFixedHeights() {
        val layout = TabularLayout("50px", "100px,200px,300px")

        val panel = JPanel(layout)
        val row0: Component = JPanel()
        val row1: Component = JPanel()

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))

        mockPackPanel(panel)

        assertThat(panel.getHeight()).isEqualTo(600)
        assertThat(row0.getHeight()).isEqualTo(100)
        assertThat(row0.getWidth()).isEqualTo(50)
        assertThat(row1.getHeight()).isEqualTo(200)
        assertThat(row1.getWidth()).isEqualTo(50)
    }

    @Test
    fun rowsCanBeConfiguredDynamically() {
        val layout = TabularLayout("50px")
        layout.setRowSizing(0, "100px")
        layout.setRowSizing(1, "200px")
        layout.setRowSizing(2, "300px")

        val panel = JPanel(layout)
        val row0: Component = JPanel()
        val row1: Component = JPanel()

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))

        mockPackPanel(panel)

        assertThat(panel.getHeight()).isEqualTo(600)
        assertThat(row0.getHeight()).isEqualTo(100)
        assertThat(row0.getWidth()).isEqualTo(50)
        assertThat(row1.getHeight()).isEqualTo(200)
        assertThat(row1.getWidth()).isEqualTo(50)
    }

    @Test
    fun rowsCanBeConfiguredWithProportionalHeights() {
        val layout = TabularLayout("*", "*,2*")

        val panel = JPanel(layout)
        val row0: Component = JPanel()
        val row1: Component = JPanel()

        panel.preferredSize = Dimension(50, 300)

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))

        mockPackPanel(panel)

        assertThat(panel.getHeight()).isEqualTo(300)
        assertThat(row0.getHeight()).isEqualTo(100)
        assertThat(row0.getWidth()).isEqualTo(50)
        assertThat(row1.getHeight()).isEqualTo(200)
        assertThat(row1.getWidth()).isEqualTo(50)
    }

    @Test
    fun cellsCanSpanAcrossMultipleRowsAndColumns() {
        val layout = TabularLayout("10px,20px,30px", "100px,200px,300px")
        val panel = JPanel(layout)

        val cellNW = JPanel()
        val cellNE = JPanel()
        val cellSW = JPanel()
        val cellSE = JPanel()

        panel.add(cellNW, TabularLayout.Constraint(0, 0, 2, 2))
        panel.add(cellNE, TabularLayout.Constraint(0, 1, 2, 2))
        panel.add(cellSW, TabularLayout.Constraint(1, 0, 2, 2))
        panel.add(cellSE, TabularLayout.Constraint(1, 1, 2, 2))

        mockPackPanel(panel)
        assertThat(cellNW.getWidth()).isEqualTo(30)
        assertThat(cellNW.getHeight()).isEqualTo(300)
        assertThat(cellNE.getWidth()).isEqualTo(50)
        assertThat(cellNE.getHeight()).isEqualTo(300)
        assertThat(cellSW.getWidth()).isEqualTo(30)
        assertThat(cellSW.getHeight()).isEqualTo(500)
        assertThat(cellSE.getWidth()).isEqualTo(50)
        assertThat(cellSE.getHeight()).isEqualTo(500)
    }

    @Test
    fun dpSizingConsidersScaleFactor() {
        val originalFactor = scale(1.0f)
        val scaleFactor = 2f
        setUserScaleFactorForTest(scaleFactor)

        val layout = TabularLayout("50dp")
        layout.setRowSizing(0, "100dp")
        layout.setRowSizing(1, "400px") // Ignore scaling

        val panel = JPanel(layout)
        val row0: Component = JPanel()
        val row1: Component = JPanel()

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))

        mockPackPanel(panel)

        assertThat(panel.getHeight()).isEqualTo(scaleFactor.toInt() * 300)
        assertThat(row0.getHeight()).isEqualTo(scaleFactor.toInt() * 100)
        assertThat(row1.getHeight()).isEqualTo(400)
        assertThat(row0.getWidth()).isEqualTo(scaleFactor.toInt() * 50)
        assertThat(row1.getWidth()).isEqualTo(scaleFactor.toInt() * 50)

        setUserScaleFactorForTest(originalFactor)
    }

    @Test
    fun proportionalSizingHandlesRoundingErrorWhenSizesRoundDown() {
        // 3 rows - each row gets 33.333...% of the space. However, since Swing sizes are integers,
        // for a panel of size 100, this would give us "33" * 3, missing a pixel. Tabular layout should
        // add back that missing space somehow.
        val layout = TabularLayout("*", "*,*,*")

        val panel = JPanel(layout)
        val row0: Component = JPanel()
        val row1: Component = JPanel()
        val row2: Component = JPanel()

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))
        panel.add(row2, TabularLayout.Constraint(2, 0))

        panel.preferredSize = Dimension(50, 100)
        mockPackPanel(panel)
        assertThat(row0.getHeight() + row1.getHeight() + row2.getHeight()).isEqualTo(100)
    }

    @Test
    fun proportionalSizingHandlesRoundingErrorWhenSizesRoundUp() {
        // 8 rows - each row gets 12.5% of the space. However, since Swing sizes are integers,
        // for a panel of size 100, this would give us "13" * 8, which is 4 pixels too large. Tabular
        // layout should remove out that extra space somehow.
        val layout = TabularLayout("*", "*,*,*,*,*,*,*,*")

        val panel = JPanel(layout)
        val row0: Component = JPanel()
        val row1: Component = JPanel()
        val row2: Component = JPanel()
        val row3: Component = JPanel()
        val row4: Component = JPanel()
        val row5: Component = JPanel()
        val row6: Component = JPanel()
        val row7: Component = JPanel()

        panel.add(row0, TabularLayout.Constraint(0, 0))
        panel.add(row1, TabularLayout.Constraint(1, 0))
        panel.add(row2, TabularLayout.Constraint(2, 0))
        panel.add(row3, TabularLayout.Constraint(3, 0))
        panel.add(row4, TabularLayout.Constraint(4, 0))
        panel.add(row5, TabularLayout.Constraint(5, 0))
        panel.add(row6, TabularLayout.Constraint(6, 0))
        panel.add(row7, TabularLayout.Constraint(7, 0))

        panel.preferredSize = Dimension(50, 100)
        mockPackPanel(panel)
        assertThat(
            row0.getHeight() +
                    row1.getHeight() +
                    row2.getHeight() +
                    row3.getHeight() +
                    row4.getHeight() +
                    row5.getHeight() +
                    row6.getHeight() +
                    row7.getHeight()
        ).isEqualTo(100)
    }

    companion object {
        /**
         * This fake pack method aims to imitate Frame.pack(), which we can't call in headless mode.
         */
        private fun mockPackPanel(panel: JPanel) {
            panel.size = panel.getPreferredSize()
            panel.doLayout()
        }
    }
}