package com.varabyte.kobweb.intellij.test.util

import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesManager
import com.intellij.find.impl.FindManagerImpl
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt

/** Returns the (trimmed) line of code this usage appears on, with the usage itself wrapped in brackets. */
private fun UsageInfo2UsageAdapter.toLineWithUsageBracketed(project: Project): String {
    val document = PsiDocumentManager.getInstance(project).getDocument(usageInfo.file!!)!!
    val usageRange = usageInfo.segment!!
    val line = document.getLineNumber(usageRange.startOffset)
    val lineStart = document.getLineStartOffset(line)
    val lineText = document.getText(TextRange(lineStart, document.getLineEndOffset(line)))

    val start = usageRange.startOffset - lineStart
    val end = usageRange.endOffset - lineStart
    return (lineText.substring(0, start) + "[" + lineText.substring(start, end) + "]" + lineText.substring(end)).trim()
}

/**
 * Find usages of the target element, the same way the Find Usages action does (which includes custom searchers).
 *
 * @return Each usage found, as the line of code it appears on (with the usage itself in brackets), prefixed with its
 *   file name. For example: `"Usage.kt: val worker = [CalculatePiWorker] { ... }"`. This mirrors what the Find Usages
 *   window shows.
 */
@OptIn(KaAllowAnalysisOnEdt::class)
fun Project.findUsages(element: PsiElement): List<String> {
    val findUsagesManager = (FindManager.getInstance(this) as FindManagerImpl).findUsagesManager
    val handler = findUsagesManager.getFindUsagesHandler(element, false)!!
    val searcher = FindUsagesManager.createUsageSearcher(
        handler,
        handler.primaryElements,
        handler.secondaryElements,
        handler.getFindUsagesOptions(null),
    )

    val usages = mutableListOf<Usage>()
    allowAnalysisOnEdt {
        // Usage searches expect to run with a progress indicator, as they do in the IDE
        ProgressManager.getInstance().runProcess(
            { searcher.generate { usage -> usages.add(usage); true } },
            EmptyProgressIndicator(),
        )
    }
    return usages
        .filterIsInstance<UsageInfo2UsageAdapter>()
        .map { usage -> "${usage.file.name}: ${usage.toLineWithUsageBracketed(this)}" }
        .sorted()
}
