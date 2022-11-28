/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.browser

import org.w3c.dom.*

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@Deprecated(
    message = "This API is moved to another package, use 'kotlinx.browser.window' instead.",
    replaceWith = ReplaceWith("window", "kotlinx.browser.window")
)
@kotlin.internal.LowPriorityInOverloadResolution
@DeprecatedSinceKotlin(warningSince = "1.4", errorSince = "1.6")
public external val window: Window

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@Deprecated(
    message = "This API is moved to another package, use 'kotlinx.browser.document' instead.",
    replaceWith = ReplaceWith("document", "kotlinx.browser.document")
)
@kotlin.internal.LowPriorityInOverloadResolution
@DeprecatedSinceKotlin(warningSince = "1.4", errorSince = "1.6")
public external val document: Document

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@Deprecated(
    message = "This API is moved to another package, use 'kotlinx.browser.localStorage' instead.",
    replaceWith = ReplaceWith("localStorage", "kotlinx.browser.localStorage")
)
@kotlin.internal.LowPriorityInOverloadResolution
@DeprecatedSinceKotlin(warningSince = "1.4", errorSince = "1.6")
public external val localStorage: Storage

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
@Deprecated(
    message = "This API is moved to another package, use 'kotlinx.browser.sessionStorage' instead.",
    replaceWith = ReplaceWith("sessionStorage", "kotlinx.browser.sessionStorage")
)
@kotlin.internal.LowPriorityInOverloadResolution
@DeprecatedSinceKotlin(warningSince = "1.4", errorSince = "1.6")
public external val sessionStorage: Storage

