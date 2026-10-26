package com.varabyte.kobweb.intellij.util.text

// Unicode Character Database data, used under the Unicode License v3
//
// The ranges below are copied from the "Derived Property: Default_Ignorable_Code_Point" section of
// DerivedCoreProperties-18.0.0.txt (dated 2026-08-07), part of the Unicode Character Database:
// https://www.unicode.org/Public/18.0.0/ucd/DerivedCoreProperties.txt
//
// © 2026 Unicode®, Inc. Unicode and the Unicode Logo are registered trademarks of Unicode, Inc. in the U.S. and other
// countries. For terms of use and license, see https://www.unicode.org/terms_of_use.html. The license's full copyright
// and permission notice ships with this plugin, at "licenses/Unicode-LICENSE.txt" in its resources (copied from
// https://www.unicode.org/license.txt).
//
// To update, copy that section from the latest version of the file
// (https://www.unicode.org/Public/UCD/latest/ucd/DerivedCoreProperties.txt), one range per line, and update the version,
// date, and copyright year above.

/**
 * The code points with Unicode's "Default_Ignorable_Code_Point" property, i.e. characters that are normally rendered as
 * nothing at all, e.g. zero-width spaces, bidirectional text controls, and variation selectors.
 *
 * Java doesn't expose this property itself (see e.g. [Character.getType], which only gives a character's general
 * category), hence this copy of Unicode's data.
 */
private val DEFAULT_IGNORABLE_CODE_POINTS = listOf(
    0x00AD..0x00AD, // SOFT HYPHEN
    0x034F..0x034F, // COMBINING GRAPHEME JOINER
    0x061C..0x061C, // ARABIC LETTER MARK
    0x115F..0x1160, // HANGUL CHOSEONG FILLER..HANGUL JUNGSEONG FILLER
    0x17B4..0x17B5, // KHMER VOWEL INHERENT AQ..KHMER VOWEL INHERENT AA
    0x180B..0x180D, // MONGOLIAN FREE VARIATION SELECTOR ONE..MONGOLIAN FREE VARIATION SELECTOR THREE
    0x180E..0x180E, // MONGOLIAN VOWEL SEPARATOR
    0x180F..0x180F, // MONGOLIAN FREE VARIATION SELECTOR FOUR
    0x200B..0x200F, // ZERO WIDTH SPACE..RIGHT-TO-LEFT MARK
    0x202A..0x202E, // LEFT-TO-RIGHT EMBEDDING..RIGHT-TO-LEFT OVERRIDE
    0x2060..0x2064, // WORD JOINER..INVISIBLE PLUS
    0x2065..0x2065, // <reserved-2065>
    0x2066..0x206F, // LEFT-TO-RIGHT ISOLATE..NOMINAL DIGIT SHAPES
    0x3164..0x3164, // HANGUL FILLER
    0xFE00..0xFE0F, // VARIATION SELECTOR-1..VARIATION SELECTOR-16
    0xFEFF..0xFEFF, // ZERO WIDTH NO-BREAK SPACE
    0xFFA0..0xFFA0, // HALFWIDTH HANGUL FILLER
    0xFFF0..0xFFF8, // <reserved-FFF0>..<reserved-FFF8>
    0x1BCA0..0x1BCA3, // SHORTHAND FORMAT LETTER OVERLAP..SHORTHAND FORMAT UP STEP
    0x1D173..0x1D17A, // MUSICAL SYMBOL BEGIN BEAM..MUSICAL SYMBOL END PHRASE
    0xE0000..0xE0000, // <reserved-E0000>
    0xE0001..0xE0001, // LANGUAGE TAG
    0xE0002..0xE001F, // <reserved-E0002>..<reserved-E001F>
    0xE0020..0xE007F, // TAG SPACE..CANCEL TAG
    0xE0080..0xE00FF, // <reserved-E0080>..<reserved-E00FF>
    0xE0100..0xE01EF, // VARIATION SELECTOR-17..VARIATION SELECTOR-256
    0xE01F0..0xE0FFF, // <reserved-E01F0>..<reserved-E0FFF>
)

/**
 * Returns true if this code point has Unicode's "Default_Ignorable_Code_Point" property, i.e. it's normally rendered as
 * nothing at all (see [DEFAULT_IGNORABLE_CODE_POINTS]).
 */
fun Int.isDefaultIgnorableCodePoint(): Boolean = DEFAULT_IGNORABLE_CODE_POINTS.any { range -> this in range }
