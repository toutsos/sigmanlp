package com.articulate.nlp.corpora;

import com.articulate.nlp.pipeline.Pipeline;
import com.articulate.nlp.pipeline.SentenceUtil;
import com.articulate.nlp.semRewrite.Literal;
import com.articulate.sigma.StringUtil;
import com.sun.org.apache.bcel.internal.classfile.LineNumber;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.util.CoreMap;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;

import static com.articulate.nlp.pipeline.SentenceUtil.toDependenciesList;

public class LogicLevel {

    ArrayList<CoreMap> sentences = new ArrayList<>();
    public static Pipeline p = null;
    private static Annotation wholeDocument = null;
    public static boolean initialized = false;
    private static boolean debug = false;

    public static final List<String> epis = Arrays.asList("know", "think",
            "learn", "understand", "perceive", "feel", "guess", "recognize",
            "notice", "want", "wish", "hope", "decide", "expect", "prefer",
            "remember", "forget", "imagine", "believe");

    public static final List<String> modal = Arrays.asList("can", "could",
            "may", "might", "must", "shall", "should", "will", "would");

    public static final List<String> otherModal = Arrays.asList("ought", "dare", "need");

    public static final List<String> quant = Arrays.asList("some", "many", "few", "all");

    public enum READ_MODE {PARA, LINE,  WORD; }
    public READ_MODE modifier = READ_MODE.PARA;

    public int negCount = 0;
    public int epiCount = 0;
    public int modalCount = 0;
    public int otherModalCount = 0;
    public int quantCount = 0;
    public int simpleCount = 0;

    public boolean showneg = false;
    public boolean showepi = false;
    public boolean showmodal = false;
    public boolean showothermodal = false;
    public boolean showquant = false;
    public boolean showall = false;

    /** ***************************************************************
     */
    public static void init() {

        if (initialized)
            return;
        System.out.println("in LogicLevel.init(): ");
        Properties props = new Properties();
        String propString = "tokenize, ssplit, pos, lemma, parse, depparse, natlog";
        p = new Pipeline(true,propString);
        initialized = true;
        System.out.println("in LogicLevel.init(): completed initialization");
    }

    /** ***************************************************************
     */
    public void process(CoreMap sent) {

        boolean simple = true;
        ArrayList<Literal> deps = SentenceUtil.toDependenciesList(sent);
        System.out.println("process(): deps: " + deps);
        for (Literal lit : deps) {
            if (lit.pred.equals("neg")) {
                if (showall || showneg) System.out.println("neg: " + sent);
                negCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
        for (CoreLabel cl : tokens) {
            if (epis.contains(cl.lemma())) {
                if (showall || showepi) System.out.println("epistemic: " + sent);
                epiCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        for (CoreLabel cl : tokens) {
            if (modal.contains(cl.lemma())) {
                if (showall || showmodal) System.out.println("modal: " + sent);
                modalCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        for (CoreLabel cl : tokens) {
            if (otherModal.contains(cl.lemma())) {
                if (showall || showothermodal) System.out.println("other modal: " + sent);
                otherModalCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        for (CoreLabel cl : tokens) {
            if (quant.contains(cl.lemma())) {
                if (showall || showquant) System.out.println("quant: " + quant);
                quantCount++;
                simple = false;
                break; // only count a sentence once
            }
        }

        if (simple)
            simpleCount++;
    }

    /** ***************************************************************
     */
    public void readParaFile(LineNumberReader lnr) throws IOException {

        String line;
        StringBuffer para = new StringBuffer();
        int linecount = 0;
        while ((line = lnr.readLine()) != null) {
            linecount++;
            if (linecount == 1000)
                System.out.print(".");
            if (StringUtil.emptyString(line)) {
                wholeDocument = new Annotation(para.toString());
                p.pipeline.annotate(wholeDocument);
                List<CoreMap> sents = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
                for (CoreMap cm : sents)
                    sentences.add(cm);
                //System.out.println("run(): sents: " + sents);
                para = new StringBuffer();
            }
            else {
                para.append(line + " ");
            }
        }
    }

    /** ***************************************************************
     */
    public void readLineFile(LineNumberReader lnr) throws IOException {

        String line;
        StringBuffer para = new StringBuffer();
        int linecount = 0;
        while ((line = lnr.readLine()) != null) {
            linecount++;
            if (linecount == 1000)
                System.out.print(".");
            if (StringUtil.emptyString(line)) {
                wholeDocument = new Annotation(para.toString());
                p.pipeline.annotate(wholeDocument);
                List<CoreMap> sents = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
                for (CoreMap cm : sents)
                    sentences.add(cm);
            }
        }
    }

    /** ***************************************************************
     */
    public void readWordFile(LineNumberReader lnr) throws IOException {

        String line;
        StringBuffer l = new StringBuffer();
        int linecount = 0;
        while ((line = lnr.readLine()) != null) {
            System.out.println(line);
            linecount++;
            if (linecount == 1000)
                System.out.print(".");
            if (line.startsWith("#")) {
                //System.out.println("readWordFile(): l: " + l.toString());
                wholeDocument = new Annotation(l.toString());
                p.pipeline.annotate(wholeDocument);
                List<CoreMap> sents = wholeDocument.get(CoreAnnotations.SentencesAnnotation.class);
                for (CoreMap cm : sents)
                    sentences.add(cm);
                //System.out.println("readWordFile(): sents: " + sents);
                l = new StringBuffer();
            }
            else {
                if (line.indexOf("\t") != -1)
                    l.append(line.substring(0,line.indexOf("\t")) + " ");
            }
        }
    }

    /** ***************************************************************
     */
    public void run(String filename) {

        HashSet<String> companies = new HashSet<>();
        System.out.println("INFO in LogicLevel.run()");
        try {
            FileReader r = new FileReader(filename);
            LineNumberReader lnr = new LineNumberReader(r);
            if (modifier == READ_MODE.PARA)
                readParaFile(lnr);
            if (modifier == READ_MODE.LINE)
                readLineFile(lnr);
            if (modifier == READ_MODE.WORD)
                readWordFile(lnr);
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        for (CoreMap s : sentences)
            process(s);
        System.out.println("negations: " + negCount);
        System.out.println("epistemics: " + epiCount);
        System.out.println("modalCount: " + modalCount);
        System.out.println("otherModalCount: " + otherModalCount);
        System.out.println("quantified: " + quantCount);
        System.out.println("simple: " + simpleCount);
        System.out.println("total: " + sentences.size());
    }

    /** ***************************************************************
     */
    public void runDir(String dir) {

        File f = new File(dir);
        String[] pathnames = f.list();
        for (String s : pathnames) {
            System.out.println("running on file: " + s);
            run(s);
        }
    }

    /** ***************************************************************
     */
    public static void showHelp() {

        System.out.println("LogicLevel corpus analysis");
        System.out.println("  options:");
        System.out.println("  -h - show this help screen");
        System.out.println("  -f fname - run on file");
        System.out.println("  -r - run on default LogicLevel corpus file");
        System.out.println("  -d dir - run on all files in directory");
        System.out.println("  ");
        System.out.println("  add a letter directly to the option (no spaces)");
        System.out.println("      p  - add a 'p' to read files with paragraph formats");
        System.out.println("      l  - add an 'l' to read files with one sentence per line");
        System.out.println("      w  - add a 'w' to read files with one word per line including lemma and POS");
    }

    /** ***************************************************************
     */
    public static void main(String[] args) {

        if (args == null || args.length == 0 ||
                (args != null && args.length > 1 && args[0].equals("-h"))) {
            showHelp();
        }
        else {
            init();
            LogicLevel logicLevel = new LogicLevel();
            if (args != null && args.length > 0) {
                if (args[0].endsWith("p"))
                    logicLevel.modifier = READ_MODE.PARA;
                if (args[0].endsWith("l"))
                    logicLevel.modifier = READ_MODE.LINE;
                if (args[0].endsWith("w"))
                    logicLevel.modifier = READ_MODE.WORD;
            }
            if (args != null && args.length > 0 && args[0].startsWith("-r")) {
                String filename = System.getenv("CORPORA") + File.separator + "logicLevel.txt";
                logicLevel.run(filename);
            }
            else if (args != null && args.length > 1 && args[0].startsWith("-f")) {
                logicLevel.run(args[1]);
            }
            else if (args != null && args.length > 1 && args[0].startsWith("-d")) {
                logicLevel.runDir(args[1]);
            }
        }
    }
}
