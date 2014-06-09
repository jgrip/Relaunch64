/*
 * Relaunch64 - A Java cross-development IDE for C64 machine language coding.
 * Copyright (C) 2001-2014 by Daniel Lüdecke (http://www.danielluedecke.de)
 * 
 * Homepage: http://www.popelganda.de
 * 
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Dieses Programm ist freie Software. Sie können es unter den Bedingungen der GNU
 * General Public License, wie von der Free Software Foundation veröffentlicht, weitergeben
 * und/oder modifizieren, entweder gemäß Version 3 der Lizenz oder (wenn Sie möchten)
 * jeder späteren Version.
 * 
 * Die Veröffentlichung dieses Programms erfolgt in der Hoffnung, daß es Ihnen von Nutzen sein 
 * wird, aber OHNE IRGENDEINE GARANTIE, sogar ohne die implizite Garantie der MARKTREIFE oder 
 * der VERWENDBARKEIT FÜR EINEN BESTIMMTEN ZWECK. Details finden Sie in der 
 * GNU General Public License.
 * 
 * Sie sollten ein Exemplar der GNU General Public License zusammen mit diesem Programm 
 * erhalten haben. Falls nicht, siehe <http://www.gnu.org/licenses/>.
 */
package de.relaunch64.popelganda.assemblers;

import de.relaunch64.popelganda.assemblers.ErrorHandler.ErrorInfo;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.ArrayList;

/**
 *
 * @author Soci/Singular
 */
class Assembler_64tass implements Assembler {
    final static String opcodes[] = { // must be sorted!
        "ADC", "ANC", "AND", "ANE", "ARR", "ASL", "ASR", "BCC", "BCS", "BEQ",
        "BGE", "BIT", "BLT", "BMI", "BNE", "BPL", "BRK", "BVC", "BVS", "CLC",
        "CLD", "CLI", "CLV", "CMP", "CPX", "CPY", "DCP", "DEC", "DEX", "DEY",
        "EOR", "GCC", "GCS", "GEQ", "GGE", "GLT", "GMI", "GNE", "GPL", "GVC",
        "GVS", "INC", "INX", "INY", "ISB", "JAM", "JMP", "JSR", "LAX", "LDA",
        "LDS", "LDX", "LDY", "LSR", "NOP", "ORA", "PHA", "PHP", "PLA", "PLP",
        "RLA", "ROL", "ROR", "RRA", "RTI", "RTS", "SAX", "SBC", "SBX", "SEC",
        "SED", "SEI", "SHA", "SHL", "SHR", "SHS", "SHX", "SHY", "SLO", "SRE",
        "STA", "STX", "STY", "TAX", "TAY", "TSX", "TXA", "TXS", "TYA"
    };
    /**
     * String array with Math functions and scripting language keywords, in
     * case the assembler supports this. Used for syntax highlighting keywords.
     */
    final static String[] scriptKeywords = {
        "abs", "acos", "asin", "atan", "atan2", "cbrt", "ceil", "cos", "cosh",
        "deg", "exp", "floor", "frac", "hypot", "log", "log10",
        "max", "min", "mod", "rad", "round", "sign", "sin", "sinh", "sort",
        "signum", "tan", "tanh", "trunc", "bool", "len", "all", "any", "str",
        "repr", "format", "range"
    };

    @Override
    public String name() {
        return "64tass";
    }

    @Override
    public String fileName() {
        return "64tass";
    }
    /**
     * Assembler ID.
     * @return the unique assembler ID.
     */
    @Override
    public int getID() {
        return Assemblers.ID_64TASS;
    }

    @Override
    public String syntaxFile() {
        return "assembly-64tass.xml";
    }

    @Override
    public String getLineComment() {
        return ";";
    }

    @Override
    public String getMacroPrefix() {
        return "#";
    }

    @Override
    public String getByteDirective() {
        return ".byte";
    }

    @Override
    public String getBasicStart(int start) {
        return "*= $801\n.word (+), 10\n.null $9e, \"" + Integer.toString(start) + "\"\n+ .word 0\n";
    }

    @Override
    public String getIncludeSourceDirective(String path) {
        return ".include \"" + path + "\"";
    }

    @Override
    public String getIncludeTextDirective(String path) {
        return ".binary \"" + path + "\"";
    }

    @Override
    public String getIncludeC64Directive(String path) {
        return ".binary \"" + path + "\",2";
    }

    @Override
    public String getIncludeBinaryDirective(String path) {
        return ".binary \"" + path + "\"";
    }

    @Override
    public String[] getScriptKeywords() {
        return scriptKeywords;
    }

    @Override
    public String getDefaultCommandLine(String fp) {
        return fp + " -C -a -i " + INPUT_FILE + " -o " + OUTPUT_FILE;
    }

    @Override
    public String getHelpCLI() {
        return " --help";
    }

    private enum labelType {
        LABEL, FUNCTION, MACRO
    }
    /**
     * Extracts all labels, functions and macros of a source code file. Information
     * on names and linenumbers of labels, functions and macros are saved as linked
     * hashmaps. Information can then be accessed via 
     * {@link Assembler.labelList#labels labelList.labels},
     * {@link Assembler.labelList#functions labelList.functions} and
     * {@link Assembler.labelList#macros labelList.macros}.
     * 
     * @param lineReader a LineNumberReader from the source code content, which is
     * created in {@link de.relaunch64.popelganda.Editor.LabelExtractor#getLabels(java.lang.String, de.relaunch64.popelganda.assemblers.Assembler, int) LabelExtractor.getLabels()}.
     * @param lineNumber the line number, from where to start the search for labels/functions/macros.
     * use 0 to extract all labels/functions/macros. use any specific line number to extract only
     * global labels/functions/macros and local labels/functions/macros within scope.
     * @return a {@link Assembler.labelList labelList} 
     * with information (names and line numbers) about all extracted labels/functions/macros.
     */
    @Override
    public labelList getLabels(LineNumberReader lineReader, int lineNumber) {
        class lineInfo {
            final LinkedList<String> name;
            final int line;
            final labelType type;
            lineInfo(LinkedList<String> name, int line, labelType type) {
                this.name = name;
                this.line = line;
                this.type = type;
            }
        }
        labelList returnValue = new labelList(null, null, null);
        LinkedList<lineInfo> labels = new LinkedList<>(), localLabels = new LinkedList<>();
        String line;
        // Daniel: I love this regex-stuff! Unfortunately I'm too old to understand it...
        Pattern p = Pattern.compile("(?i)^\\s*(?:(?<label>[\\p{javaUnicodeIdentifierStart}_][\\p{javaUnicodeIdentifierPart}_.]*\\b):?)?\\s*(?<directive>\\.(?:block|bend|proc|pend|function|endf|macro|segment|endm|struct|ends|union|endu)\\b)?.*");
        LinkedList<String> myscope = new LinkedList<>(), scopes = new LinkedList<>();
        boolean scopeFound = false;
        try {
            while ((line = lineReader.readLine()) != null) {
                if (lineReader.getLineNumber() == lineNumber) myscope = (LinkedList)scopes.clone();

                Matcher m = p.matcher(line);
                if (!m.matches()) continue;

                String label = m.group("label");
                LinkedList<String> newlabel;
                boolean local;
                if (label != null) {
                    local = (label.charAt(0) == '_');

                    if (local) {
                        newlabel = new LinkedList();
                    } else {
                        if (label.length() == 3 && Arrays.binarySearch(opcodes, label.toUpperCase()) >= 0) {
                            continue; // ignore opcodes
                        }
                        newlabel = (LinkedList)scopes.clone();
                    }
                    newlabel.addLast(label);
                } else {
                    local = false;
                    newlabel = null;
                }

                String directive = m.group("directive"); // track scopes
                labelType type = labelType.LABEL;
                if (directive != null) {
                    switch (directive.toLowerCase()) {
                        case ".block":
                        case ".proc":
                        case ".struct":
                        case ".union":
                            if (label != null) scopes.add(label); // new scope
                            else scopes.add("");
                            break;
                        case ".bend":
                        case ".pend":
                        case ".endf":
                        case ".endm":
                        case ".ends":
                        case ".endu":
                            if (!scopes.isEmpty()) scopes.removeLast(); // leave scope
                            break;
                        case ".macro":
                        case ".segment":
                            type = labelType.MACRO;
                            scopes.add("");
                            break;
                        case ".function":
                            type = labelType.FUNCTION;
                            scopes.add("");
                            break;
                    }
                }
                if (newlabel != null) {
                    if (local) {
                        if (!scopeFound && lineNumber > 0) localLabels.add(new lineInfo(newlabel, lineReader.getLineNumber(), type));
                    } else {
                        labels.add(new lineInfo(newlabel, lineReader.getLineNumber(), type));
                        if (!scopeFound && lineNumber > 0) {
                            if (lineNumber < lineReader.getLineNumber()) {
                                scopeFound = true;
                            } else {
                                localLabels.clear();
                            }
                        }
                    }
                }
            }
        }
        catch (IOException ex) {
        }
        for (lineInfo label : localLabels) {
            LinkedHashMap map;
            switch (label.type) {
                default:
                case LABEL: map = returnValue.labels; break;
                case FUNCTION: map = returnValue.functions; break;
                case MACRO: map = returnValue.macros; break;
            }
            map.put(label.name, label.line);
        }
        // Simple global scope
        if (myscope.isEmpty() || lineNumber < 1) {
            for (lineInfo label : labels) {
                String fullLabel;
                StringBuilder kbuild = new StringBuilder();
                boolean first = false, anon = false;

                for (String name : label.name) { // build full name
                    if (name.length() == 0) {
                        anon = true;
                        break;
                    }
                    if (first) kbuild.append('.');
                    kbuild.append(name);
                    first = true;
                }
                if (anon) continue;

                fullLabel = kbuild.toString();
                LinkedHashMap map;
                switch (label.type) {
                    default:
                    case LABEL: map = returnValue.labels; break;
                    case FUNCTION: map = returnValue.functions; break;
                    case MACRO: map = returnValue.macros; break;
                }
                map.put(fullLabel, label.line);
            }
            return returnValue;
        }
        // Local scope
        for (lineInfo label : labels) {
            ListIterator<String> myscopei = myscope.listIterator(0);
            StringBuilder kbuild = new StringBuilder();
            boolean different = false, anon = false;

            for (String name : label.name) {
                if (different) {
                    kbuild.append('.');
                } else {
                    if (myscopei.hasNext() && myscopei.next().equals(name)) continue;
                    different = true;
                }
                if (name.length() == 0) {
                    anon = true;
                    break;
                }
                kbuild.append(name);
            }
            if (anon) continue;

            if (!different) kbuild.append(label.name.getLast());
            if (kbuild.length() == 0) continue;

            String fullLabel = kbuild.toString();
            LinkedHashMap map;
            switch (label.type) {
                default:
                case LABEL: map = returnValue.labels; break;
                case FUNCTION: map = returnValue.functions; break;
                case MACRO: map = returnValue.macros; break;
            }
            map.put(fullLabel, label.line);
        }
        return returnValue;
    }
    /**
     * Returns the label name part before the cursor.
     * 
     * @param line Currect line content
     * @param pos Caret position
     * 
     * @return Label name part before the cursor.
     */
    @Override
    public String labelGetStart(String line, int pos) {
        String line2 = new StringBuffer(line.substring(0, pos)).reverse().toString();
        Pattern p = Pattern.compile("([\\p{javaUnicodeIdentifierPart}_.]*[\\p{javaUnicodeIdentifierStart}_]\\b)([^\\p{javaUnicodeIdentifierPart}_.].*|$)");
        Matcher m = p.matcher(line2);
        if (!m.matches()) return "";
        return new StringBuffer(m.group(1)).reverse().toString();
    }
    /**
     * Parses the error messages from the error log and adds the information
     * to the {@link ErrorHandler.ErrorInfo}.
     * 
     * @param lineReader a LineNumberReader from the error log, which is created
     * by {@link ErrorHandler#readErrorLines(java.lang.String, de.relaunch64.popelganda.assemblers.Assembler) readErrorLines()}.
     * @return an ArrayList of {@link ErrorHandler.ErrorInfo} for
     * each logged error.
     */
    @Override
    public ArrayList<ErrorInfo> readErrorLines(LineNumberReader lineReader) {
        final ArrayList<ErrorInfo> errors = new ArrayList<>();
        String line;     // j.asm:4:5: error: not defined 'i'
        Pattern p = Pattern.compile("^(?<file>.*?):(?<line>\\d+):(?<col>\\d+): (?:error|warning):.*");
        try {
            while ((line = lineReader.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (!m.matches()) continue;
                ErrorInfo e = new ErrorInfo(
                        Integer.parseInt(m.group("line")),
                        Integer.parseInt(m.group("col")),
                        lineReader.getLineNumber(), 1,
                        m.group("file")
                        );
                errors.add(e);
            }
        }
        catch (IOException ex) {
        }
        return errors;
    }

    private static final Pattern directivePattern = Pattern.compile("^\\s*(?:[\\p{javaUnicodeIdentifierStart}_][\\p{javaUnicodeIdentifierPart}_.]*\\b:?|[+-])?\\s*(?<directive>\\.[a-zA-Z]+\\b)?.*");

    // folding according to compiler directives, plus manual folding
    @Override
    public int getFoldLevel(String line, int foldLevel) {
        Matcher m = directivePattern.matcher(line);

        if (m.matches()) {
            String directive = m.group("directive");
            if (directive != null) {
                switch (directive.toLowerCase()) {
                case ".block":
                case ".proc":
                case ".function":
                case ".macro":
                case ".segment":
                case ".struct":
                case ".union":
                case ".logical":
                case ".weak":
                case ".switch":
                case ".if":
                case ".ifeq":
                case ".ifne":
                case ".ifpl":
                case ".ifmi":
                case ".rept":
                case ".for":
                case ".section":
                    foldLevel++;
                    break;
                case ".bend":
                case ".pend":
                case ".endf":
                case ".endm":
                case ".ends":
                case ".endu":
                case ".here":
                case ".endweak":
                case ".endswitch":
                case ".fi":
                case ".endif":
                case ".next":
                case ".send":
                    foldLevel--;
                    break;
                }
            }
        }
        boolean quote = false;
        boolean quote2 = false;
        boolean comment = false;
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (comment) {
                switch (line.charAt(i)) {
                case '{': 
                    if (count < 0) count = 0;
                    count++;
                    if (count == 3) {
                        count = 0;
                        foldLevel++;
                    }
                    break;
                case '}': 
                    if (count > 0) count = 0;
                    count--;
                    if (count == -3) {
                        count = 0;
                        foldLevel--;
                    }
                    break;
                default: count = 0;
                }
                continue;
            }
            switch (line.charAt(i)) {
            case '"': if (!quote2) quote = !quote; break;
            case '\'': if (!quote) quote2 = !quote2; break;
            case ';': if (!quote && !quote2) comment = true; break;
            }
        }
        return foldLevel;
    }
}
