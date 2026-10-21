package com.varabyte.kobweb.intellij.test.util

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModChooseAction
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor

/**
 * Returns the actions this popup offers, keyed by the text the user would see for each.
 *
 * Tests can't show popups, so this lets them check the choices and pick one directly instead.
 */
fun ModChooseAction.choicesByName(context: ActionContext): Map<String, ModCommandAction> {
    return actions().associateBy { action -> action.getPresentation(context)!!.name() }
}

/**
 * Performs this action and applies the result, as if the user had invoked it.
 *
 * Any interactive parts of the result (e.g. popups or navigation) are skipped, since tests can't show them.
 */
fun ModCommandAction.performAndExecute(context: ActionContext) {
    // The platform documents (but doesn't enforce) that ModCommands must be executed inside a command but outside a
    // write action, so we check it ourselves
    check(!ApplicationManager.getApplication().isWriteAccessAllowed) {
        "ModCommands must be executed outside of a write action"
    }
    CommandProcessor.getInstance().executeCommand(context.project(), {
        ModCommandExecutor.getInstance().executeInBatch(context, perform(context))
    }, null, null)
}
