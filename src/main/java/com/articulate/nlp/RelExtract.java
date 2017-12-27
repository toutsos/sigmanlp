package com.articulate.nlp;

import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.CNF;
import com.articulate.nlp.semRewrite.Clause;
import com.articulate.nlp.semRewrite.Interpreter;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.nlp.semconcor.Searcher;
import com.articulate.sigma.*;
import com.articulate.sigma.nlg.LanguageFormatter;
import com.articulate.sigma.nlg.NLGUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.TypesafeMap;

import java.util.*;

/**
 2015-2017 Articulate Software
 2017-     Infosys

 Author: Adam Pease apease@articulatesoftware.com

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program ; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 MA  02111-1307 USA

 * Extract relation expressions based on SUMO relation &%format expressions
 */
public class RelExtract {

    public static boolean debug = false;

    /** *************************************************************
     * Copy SUMO categories in the outputMap that are associated with
     * tokens into the tokens in the cnf
     */
    public static void addCategories(CNF cnf, ArrayList<CoreLabel> outputMap) {

        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                for (CoreLabel cl : outputMap) {
                    //if (debug) System.out.println("addCategories(): " + l.clArg1.toString() + " =? " + cl.toString());
                    if (cl.toString().equals(l.clArg1.toString())) {
                        //if (debug) System.out.println("addCategories(): " + l.clArg1.category());
                        l.clArg1.setCategory(cl.category());
                    }
                    if (cl.toString().equals(l.clArg2.toString())) {
                        //if (debug) System.out.println("addCategories(): " + l.clArg1.category());
                        l.clArg2.setCategory(cl.category());
                    }
                }
            }
        }
    }

    /** *************************************************************
     * Remove the standard phrasing at the beginning of a NLG-produced
     * sentence and get the dependency parse for it.  Add a period at the
     * end of the sentence
     */
    public static CNF toCNF(Interpreter interp, String input, ArrayList<CoreLabel> outputMap) {

        input = LanguageFormatter.removePreamble(input);
        input = Character.toUpperCase(input.charAt(0)) + input.substring(1) + ".";
        if (debug) System.out.println("toCNF(): input " + input);
        //System.out.println(interp.p.toDependencies(result));
        //return interp.interpretGenCNF(input);
        //CNF result = interp.p.toCNFDependencies(input);
        CNF result = interp.p.toCNFEdgeDependencies(input);
        addCategories(result,outputMap);
        if (debug) System.out.println("toCNF():result " + result);
        return result;
    }

    /** *************************************************************
     */
    public static String removeFormatChars(String s) {

        s = s.replaceAll("&%","");
        s = s.replaceAll("%n\\{[^}]+\\}","");
        s = s.replaceAll("%.","");
        s = s.replaceAll("\\{","");
        s = s.replaceAll("\\}","");
        s = s.replaceAll("  "," ");
        return s;
    }

    /** *************************************************************
     * Retain only the literals involving tokens in the dependency parse that also are
     * from the original format string.  Literals involving tokens that are arguments and
     * not from the format string will have a non-empty type (which means a CoreLabel.category()).
     */
    public static CNF formatWordsOnly(CNF cnfinput, String format) {

        if (debug) System.out.println("RelExtract.formatWordsOnly(): " + cnfinput);
        List<Literal> literalList = cnfinput.toLiterals();
        CNF cnf = new CNF();
        String[] wordsAr = format.split(" ");
        ArrayList<String> words = new ArrayList<>();
        words.addAll(Arrays.asList(wordsAr));
        if (debug)  System.out.println("RelExtract.formatWordsOnly(): input: " + Arrays.asList(wordsAr).toString());
        for (Literal lit : literalList) {
            if (debug) System.out.println("RelExtract.formatWordsOnly(): lit: " + lit);
            if (debug) System.out.println("RelExtract.formatWordsOnly(): arg1 cat: " + lit.clArg1.category());
            if (debug) System.out.println("RelExtract.formatWordsOnly(): arg2 cat: " + lit.clArg2.category());
            if (StringUtil.emptyString(lit.clArg1.category())) {
                cnf.append(lit);
            }
            else if (StringUtil.emptyString(lit.clArg2.category())) {
                cnf.append(lit);
            }
        }
        if (debug) System.out.println("RelExtract.formatWordsOnly(): result: " + cnf);
        return cnf;
    }

    /** *************************************************************
     */
    public static String buildFormulaString(KB kb, String rel) {

        ArrayList<String> sig = kb.kbCache.signatures.get(rel);
        StringBuffer sb = new StringBuffer();
        if (sig != null && sig.size() > 1) {
            sb.append("(exists (");
            int counter = 1;
            for (String s : sig) {
                if (!StringUtil.emptyString(s)) {
                    String varname = s.toUpperCase();
                    if (s.charAt(s.length() - 1) == '+')
                        varname = varname.substring(0, s.length() - 1);
                    sb.append("?" + varname + Integer.toString(counter++) + " ");
                }
            }
            sb.append(") ");
            counter = 1;
            sb.append("(" + rel + " ");
            for (String s : sig) {
                if (!StringUtil.emptyString(s)) {
                    String varname = s.toUpperCase();
                    if (s.charAt(s.length() - 1) == '+')
                        varname = varname.substring(0, s.length() - 1);
                    sb.append("?" + varname + Integer.toString(counter++) + " ");
                }
            }
            sb.append("))");
            return sb.toString();
        }
        return "";
    }

    /** *************************************************************
     * Return a verion of the CNF input where words found in the
     * formatString are turned into word variables like "part*" that
     * requires the word but not its token number to match, and makes
     * all other constants in the CNF into variables.
     */
    public static CNF toVariables(CNF input, String formatString) {

        if (debug) System.out.println("RelExtract.toVariables(): input: " + input);
        for (Clause c : input.clauses) {
            for (Literal lit : c.disjuncts) {
                if (StringUtil.emptyString(lit.clArg1.category()))
                    lit.clArg1.set(LanguageFormatter.VariableAnnotation.class,lit.clArg1.value() + "*");
                else
                    lit.clArg1.set(LanguageFormatter.VariableAnnotation.class,"?" + lit.clArg1.value() + "-" + lit.clArg1.index());
                if (StringUtil.emptyString(lit.clArg2.category()))
                    lit.clArg2.set(LanguageFormatter.VariableAnnotation.class,lit.clArg2.value() + "*");
                else
                    lit.clArg2.set(LanguageFormatter.VariableAnnotation.class,"?" + lit.clArg2.value() + "-" + lit.clArg2.index());
                //if (!lit.arg1.endsWith("*"))
                //    lit.setarg1("?" + lit.arg1);
                //if (!lit.arg2.endsWith("*"))
                 //   lit.setarg2("?" + lit.arg2);
            }
        }
        if (debug) System.out.println("RelExtract.toVariables(): result: " + printCNFVariables(input));
        return input;
    }

    /** *************************************************************
     */
    public static CNF removeRoot(CNF input) {

        CNF newcnf = new CNF();
        List<Literal> lits = input.toLiterals();
        for (Literal lit : lits)
            if (!lit.pred.equals("root"))
                newcnf.append(lit);
        return newcnf;
    }

    /** *************************************************************
     */
    public static CNF removeDet(CNF input) {

        CNF newcnf = new CNF();
        List<Literal> lits = input.toLiterals();
        for (Literal lit : lits)
            if (!lit.pred.equals("det"))
                newcnf.append(lit);
        return newcnf;
    }

    /** *************************************************************
     * only check for words from the original format statement, which
     * do not have a CoreNLP category() value
     */
    public static boolean stopWordsOnly(CNF cnf) {

        if (debug) System.out.println("stopWordsOnly(): " + cnf);
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (l.pred.equals("sumo") || l.pred.equals("isSubclass"))
                    continue;
                //printCoreLabel(l.clArg1);
                if (l.clArg1 != null && l.clArg1.category() == null) {
                    if (!WordNet.wn.isStopWord(l.clArg1.word())) {
                        if (debug) System.out.println("stopWordsOnly(): found non-stop word " + l.clArg1.word());
                        return false;
                    }
                    else if (debug) System.out.println("stopWordsOnly(): found stop word " + l.clArg1.word());
                }

                //printCoreLabel(l.clArg2);
                if (l.clArg2 != null && l.clArg2.category() == null) {
                    if (!WordNet.wn.isStopWord(l.clArg2.word())) {
                        if (debug) System.out.println("stopWordsOnly(): found non-stop word " + l.clArg2.word());
                        return false;
                    }
                    else if (debug) System.out.println("stopWordsOnly(): found stop word " + l.clArg2.word());
                }
            }
        }
        if (debug) System.out.println("stopWordsOnly(): " + cnf + " has only stop words");
        return true;
    }

    /** *************************************************************
     */
    public static CNF promoteVariables(CNF pattern) {

        CNF result = new CNF();
        for (Clause c : pattern.clauses) {
            for (Literal l : c.disjuncts) {
                Literal newl = new Literal(l);
                String var1 = l.clArg1.getString(LanguageFormatter.VariableAnnotation.class);
                if (!StringUtil.emptyString(var1))
                    newl.clArg1.setValue(var1);
                newl.arg1 = newl.clArg1.value();
                String var2 = l.clArg2.getString(LanguageFormatter.VariableAnnotation.class);
                if (!StringUtil.emptyString(var2))
                    newl.clArg2.setValue(var2);
                result.append(newl);
                newl.arg2 = newl.clArg2.value();
            }
        }
        return result;
    }

    /** *************************************************************
     */
    public static void searchForOnePattern(String rel, CNF pattern) {

        ArrayList<String> dependencies = null;
        ArrayList<String> sentences = null;
        String dbFilepath = "wikipedia/wiki1";
        pattern = promoteVariables(pattern); // make the variable annotation the value
        if (!stopWordsOnly(pattern)) {
            try {
                CNF noTypes = removeTypes(pattern);
                System.out.println("searchForOnePattern(): no types: " + noTypes);
                dependencies = new ArrayList<String>();
                sentences = new ArrayList<String>();
                Searcher.search(dbFilepath, "", noTypes.toString(), sentences, dependencies);
                if (sentences.size() > 0) {
                    for (int i = 0; i < sentences.size(); i++) {
                        System.out.println("test(): without types: relation: " + rel);
                        System.out.println("cnf: " + noTypes.toString());
                        System.out.println("stop words only: " + stopWordsOnly(noTypes));
                        System.out.println("dep: " + dependencies.get(i));
                        System.out.println("sentence: " + sentences.get(i));
                        System.out.println();
                    }
                }
                dependencies = new ArrayList<String>();
                sentences = new ArrayList<String>();
                System.out.println("searchForOnePattern(): with types: " + pattern);
                Searcher.search(dbFilepath, "", pattern.toString(), sentences, dependencies);
                if (sentences.size() > 0) {
                    for (int i = 0; i < sentences.size(); i++) {
                        System.out.println("test(): relation: " + rel);
                        System.out.println("cnf: " + pattern.toString());
                        System.out.println("stop words only: " + stopWordsOnly(pattern));
                        System.out.println("dep: " + dependencies.get(i));
                        System.out.println("sentence: " + sentences.get(i));
                        System.out.println();
                    }
                }
            }
            catch (Exception e) {
                System.out.println("Error in RelExtract.test()");
                e.printStackTrace();
            }
        }
        else
            System.out.println("test(): stop words only in: " + pattern);
    }

    /** *************************************************************
     */
    public static void test(HashMap<String,CNF> patterns) {

        System.out.println("*************** RelExtract.test() ***********************");
        for (String rel : patterns.keySet()) {
            searchForOnePattern(rel, patterns.get(rel));
        }
    }

    /** *************************************************************
     */
    public static CNF removeTypes(CNF cnfInput) {

        CNF cnfnew = new CNF();
        List<Literal> cnflist = cnfInput.toLiterals();
        for (Literal l : cnflist)
            if (!l.pred.equals("sumo") && !l.pred.equals("isSubclass"))
                cnfnew.append(l);
        return cnfnew;
    }

    /** *************************************************************
     */
    public static HashSet<Literal> generateSUMO(CoreLabel cl, HashSet<String> typeGenerated, int varnum) {

        //System.out.println("generateSUMO(): ");
        //printCoreLabel(cl);
        HashSet<Literal> result = new HashSet<>();
        String arg = Integer.toString(cl.index());
        int argnum = Integer.parseInt(arg);
        String type = "";
        type = cl.category();
        if (StringUtil.emptyString(type)) {
            System.out.println("RelExtract.generateSUMO(): no type found for " + cl);
            return null;
        }
        if (typeGenerated.contains(type))
            return null;
        typeGenerated.add(cl.toString());
        if (!type.endsWith("+")) {
            result.add(new Literal("sumo(" + type + ",?" + cl + ")"));
            return result;
        }
        else {
            result.add(new Literal("sumo(?TYPEVAR" + Integer.toString(varnum) + "," + cl + ")"));
            result.add(new Literal("isSubclass(" + type.substring(0,type.length()-1) + ",?TYPEVAR" + Integer.toString(varnum) + ")"));
            varnum++;
        }
        return result;
    }

    /** *************************************************************
     */
    public static CNF addTypes(CNF cnfInput) {

        HashSet<String> typeGenerated = new HashSet<>();
        HashSet<Literal> sumoLit = new HashSet<>();
        int varnum = 0;
        CNF cnfnew = new CNF();
        List<Literal> cnflist = cnfInput.toLiterals();
        for (Literal l : cnflist) {
            cnfnew.append(l);
            HashSet<Literal> temp = generateSUMO(l.clArg1,typeGenerated,varnum);
            if (temp != null)
                sumoLit.addAll(temp);
            temp = generateSUMO(l.clArg2,typeGenerated,varnum);
            if (temp != null)
                sumoLit.addAll(temp);
        }
        if (sumoLit != null)
            cnfnew.appendAll(sumoLit);
        return cnfnew;
    }

    /** *************************************************************
     * Process one SUMO format statement to create a dependency parse
     * pattern that can match free text.  Use language generation
     * to create a sentence from the format statement, then run
     * dependency parsing, then modify the dependency parse to keep
     * just the essential parts of the pattern and add SUMO type
     * restrictions.
     *
     * Words appearing in the format statement become word variables (word*),
     * words that are the arguments to the relation become free variables
     * (?word) that have an associated sumo type
     */
    public static HashMap<String,CNF> processOneRelation(Formula form,
                  KB kb, Interpreter interp) {

        HashMap<String,CNF> resultSet = new HashMap<String,CNF>();
        String rel = form.getArgument(2);
        if (rel.endsWith("Fn"))
            return null;
        String formatString = form.getArgument(3);
        String formulaString = buildFormulaString(kb,rel);
        if (StringUtil.emptyString(formatString))
            return null;

        System.out.println();
        System.out.println("processOneRelation(): formula: " + formulaString);
        String nlgString = StringUtil.filterHtml(NLGUtils.htmlParaphrase("", formulaString,
                kb.getFormatMap("EnglishLanguage"), kb.getTermFormatMap("EnglishLanguage"), kb, "EnglishLanguage"));
        System.out.println("nlg: " + nlgString);
        System.out.println("output map: " + NLGUtils.outputMap);

        if (StringUtil.emptyString(nlgString))
            return null;
        CNF cnf = toCNF(interp, nlgString, NLGUtils.outputMap);
        formatString = removeFormatChars(formatString);
        CNF filtered = formatWordsOnly(cnf, formatString);
        filtered = toVariables(filtered, formatString);
        System.out.println(filtered);
        filtered = removeRoot(filtered);
        filtered = removeDet(filtered);
        HashSet<Literal> litSet = new HashSet<>();
        litSet.addAll(filtered.toLiterals());
        System.out.println(litSet);
        CNF cnfResult = CNF.fromLiterals(litSet);
        System.out.println("processOneRelation(): without types: " + cnfResult);
        CNF withTypes = addTypes(cnfResult);
        System.out.println("processOneRelation(): with types: " + withTypes);
        if (!stopWordsOnly(cnfResult))
            resultSet.put(rel, withTypes);
        System.out.println("processOneRelation(): result: " + resultSet);
        System.out.println();
        return resultSet;
    }

    /** *************************************************************
     * Process all the format statements in SUMO to create dependency
     * parse templates that match them.
     */
    public static HashMap<String,CNF> process() {

        System.out.println("RelExtract.process()");
        HashMap<String,CNF> resultSet = new HashMap<String,CNF>();
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        KB kb = KBmanager.getMgr().getKB("SUMO");
        ArrayList<Formula> forms = kb.ask("arg",0,"format");
        for (Formula f : forms) {
            HashMap<String,CNF> temp = processOneRelation(f,kb,interp);
            if (temp != null)
                resultSet.putAll(temp);
        }
        return resultSet;
    }

    /** *************************************************************
     * only check for words from the original format statement, which
     * do not have a CoreNLP category() value
     */
    public static String printCNFVariables(CNF cnf) {

        StringBuffer sb = new StringBuffer();
        if (debug) System.out.println("printCNFVariables(): " + cnf);
        for (Clause c : cnf.clauses) {
            for (Literal l : c.disjuncts) {
                if (!StringUtil.emptyString(sb.toString()))
                    sb.append(", ");
                sb.append(l.pred + "(");
                if (!StringUtil.emptyString(l.clArg1.getString(LanguageFormatter.VariableAnnotation.class)))
                    sb.append(l.clArg1.getString(LanguageFormatter.VariableAnnotation.class));
                else {
                    if (l.clArg1.index() == -1)
                        sb.append(l.clArg1.value());
                    else
                        sb.append(l.clArg1.toString());
                }
                sb.append(",");
                if (!StringUtil.emptyString(l.clArg2.getString(LanguageFormatter.VariableAnnotation.class)))
                    sb.append(l.clArg2.getString(LanguageFormatter.VariableAnnotation.class));
                else {
                    if (l.clArg2.index() == -1)
                        sb.append(l.clArg2.value());
                    else
                        sb.append(l.clArg2.toString());
                }
                sb.append(")");
            }
        }
        return sb.toString();
    }

    /** *************************************************************
     */
    public static void testProcessAndSearch() {

        System.out.println("RelExtract.testProcess()");
        String rel = "engineeringSubcomponent";
        Formula f = new Formula("(format EnglishLanguage engineeringSubcomponent \"%1 is %n a &%component of %2\")");
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        KB kb = KBmanager.getMgr().getKB("SUMO");
        HashMap<String,CNF> result = processOneRelation(f,kb,interp);
        System.out.println("RelExtract.testProcess(): " + printCNFVariables(result.get(rel)));
        searchForOnePattern(rel,result.get(rel));
    }

    /** *************************************************************
     */
    public static void testProcess() {

        System.out.println("RelExtract.testProcess()");
        Formula f = new Formula("(format EnglishLanguage engineeringSubcomponent \"%1 is %n a &%component of %2\")");
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        KB kb = KBmanager.getMgr().getKB("SUMO");
        HashMap<String,CNF> result = processOneRelation(f,kb,interp);
        System.out.println("RelExtract.testProcess(): " + printCNFVariables(result.get("engineeringSubcomponent")));
    }

    /** *************************************************************
     * Show the useful fields of a CoreLabel.  We're not concerned
     * with character-level information at our level of analysis.
     */
    public static void printCoreLabel(CoreLabel cl) {

        //System.out.println("after: " + cl.after());
        //System.out.println("before: " + cl.before());
        //System.out.println("beginPosition: " + cl.beginPosition());
        System.out.println("category: " + cl.category());
        //System.out.println("docID: " + cl.docID());
        //System.out.println("endPosition: " + cl.endPosition());
        System.out.println("index: " + cl.index());
        System.out.println("lemma: " + cl.lemma());
        System.out.println("ner: " + cl.ner());
        System.out.println("originalText: " + cl.originalText());
        System.out.println("sentIndex: " + cl.sentIndex());
        System.out.println("tag: " + cl.tag());
        System.out.println("toString: " + cl.toString());
        System.out.println("value: " + cl.value());
        System.out.println("word: " + cl.word());
        System.out.println("keyset: " + cl.keySet());
        System.out.println("variable: " + cl.getString(LanguageFormatter.VariableAnnotation.class));
        System.out.println();
    }

    /** *************************************************************
     */
    public static void testCoreLabel() {

        String input = "Robert is a tall man.";
        System.out.println("RelExtract.testCoreLabel():");
        KBmanager.getMgr().initializeOnce();
        Interpreter interp = new Interpreter();
        try {
            interp.initialize();
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        KB kb = KBmanager.getMgr().getKB("SUMO");
        Annotation anno = interp.p.annotate(input);
        List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
        System.out.println("RelExtract.testCoreLabel(): input: " + input);
        for (CoreMap sentence : sentences) {
            List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
            for (CoreLabel cl : tokens) {
                printCoreLabel(cl);
            }
        }
    }

    /** *************************************************************
     */
    public static void testStopWordsOnly() {

        KBmanager.getMgr().initializeOnce();
        System.out.println("testStopWordsOnly(): not all should be true");
        String c = "cc(?relation-2,and*), cop(?disjoint-8,are*).";
        CNF cnf = new CNF(c);
        System.out.println("expression: " + cnf);
        System.out.println(stopWordsOnly(cnf));
        c = "case(?human-10,of*), cop(?name-7,is*).";
        cnf = new CNF(c);
        System.out.println("expression: " + cnf);
        System.out.println(stopWordsOnly(cnf));
        c = "nmod:of(sale*,?transaction-9), dep(brokers*,sale*), compound(brokers*,?agent-2), case(?transaction-9,of*).";
        cnf = new CNF(c);
        System.out.println("expression: " + cnf);
        System.out.println(stopWordsOnly(cnf));
    }

    /** *************************************************************
     */
    public static void main(String[] args) {

        //testCoreLabel();
        //testProcess();
        //testStopWordsOnly();
        //HashMap<String,CNF> resultSet = process();
        //test(resultSet);

        testProcessAndSearch();
    }
}
