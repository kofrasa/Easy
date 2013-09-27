
import java.util.*;
import java.util.regex.Pattern;

/**
 * A lexical analyzer for the Easy language.
 * 
 * @author Francis <fasante@ashesi.edu.gh>
 * 
 * Coding Rules
 * ------------
 * Tokenizer functions must always return a valid token.
 * Tokenizer functions must start processing valid token from current index value
 * Tokenizer functions must point index to next character before returning
 * whenever leaving a loop with a break statement, check and correct the index counter
 */
public class Lex {

    public static final int IF=0, HALT=1, READ=2, WRITE=3, BEGIN=4, END=5, EOLN=6,
        LT=7, LTE=8, GT=9, GTE=10, EQU=11, NEQ=12, GOTO=13, SETEQ=14, COLON=15,
        PLUS=16, MINUS=17, TIMES=18, DIVIDE=19, LBRAK=20, RBRAK=21, JUNK=22,
        IDENT=23, NUMBER=24;

    private Map<String, Integer> stringTokens;
    private Map<String, Integer> symbolTokens;
    private String text;
    private String token;
    private int length;
    private int index;
    private char chr;

    public Lex() {
        stringTokens = new HashMap<String, Integer>();        
        stringTokens.put("^if$", IF);
        stringTokens.put("^halt$", HALT);
        stringTokens.put("^read$", READ);
        stringTokens.put("^write$", WRITE);
        stringTokens.put("^begin$", BEGIN);
        stringTokens.put("^end$", END);
        stringTokens.put("^goto$", GOTO);
        stringTokens.put("^[a-z]([0-9]|[a-z])*$", IDENT);

        symbolTokens = new HashMap<String, Integer>();
        symbolTokens.put("<", LT);
        symbolTokens.put("<=", LTE);
        symbolTokens.put(">", GT);
        symbolTokens.put(">=", GTE);
        symbolTokens.put("==", EQU);
        symbolTokens.put("!=", NEQ);
        symbolTokens.put("=", SETEQ);
        symbolTokens.put(":", COLON);
        symbolTokens.put("+", PLUS);
        symbolTokens.put("-", MINUS);
        symbolTokens.put("*", TIMES);
        symbolTokens.put("/", DIVIDE);
        symbolTokens.put("(", LBRAK);
        symbolTokens.put(")", RBRAK);
        symbolTokens.put("//", -100); //inline comment
    }

    public void set(String source){
        this.text = source.trim();
        this.index = 0;
        this.length = this.text.length();
    }

    public int next(){
        token = "";
        if(index < length){
            //pick one character at a time.
            //ignore white spaces ensures that every call to the the sub functions returns a token
            while((chr = text.charAt(index)) == ' ') index++;
            if(Character.isDigit(chr)){ //tokenize numbers
                return readNumber();
            }
            else if(Character.isLowerCase(chr)){ //tokenize reserved words and idents
                return readString();
            }
            else { //tokenize operators, symbols and junks
                return readSymbol();
            }
        }
        token = null;
        return EOLN;
    }
	
	public int peek(){
		int idx = index;
        String tok = token;
		int look = next();
		index = idx;
        token = tok;
		return look;
	}

    public String str(){
        return token;
    }

    /*
     * Reads an identifier or reserved word
     * @return an IDENT or reserved word token
     */
    private int readString(){
        String pattern = null;
        String chunk = "";
        boolean match = false;
        while(index < length){
            chr = text.charAt(index++);
            chunk += "" + chr;
            Iterator<String> iter = stringTokens.keySet().iterator();
            String regex = null;
            match = false;
            while(iter.hasNext()){ //find match using regex
                regex = iter.next();
                if(Pattern.matches(regex, chunk)){
                    token = chunk;
                    pattern = regex;
                    match = true;
                    break;
                }
            }
            //if match already exists, and addition of next character does not match, return.
            if(token.length() > 0 && !match) {
                index--; //reduce by one character since looking ahead moved the pointer
                break;
            }
        }
        return stringTokens.get(pattern);
    }

    /*
     * Reads a number
     * @return a NUMBER token
     */
    private int readNumber(){
        while(index < length && Character.isDigit(chr = text.charAt(index))){
            token += "" + chr;
            index++;
        }
        return NUMBER;
    }

    /*
     * Reads a symbol
     * @return a symbol or JUNK token
     */
    private int readSymbol(){
        String junk = ""; //junks are buffered
        int idx = index;
        ArrayList<String> syms = new ArrayList<String>();
        while(index < length){
            chr = text.charAt(index++);            
            if(Pattern.matches("([0-9a-z\\s])+", ""+chr)){ //leave if non-symbol is encountered
                index--;
                break;
            }
            //get invalid chars and buffer them
            if(!Pattern.matches("(/|\\<|\\>|!|=|:|\\+|\\-|\\*|\\(|\\))", ""+chr)){
                junk += ""+chr;
                continue;
            }
            syms.clear();
            //get symbols starting with current character
            for(String symbol : symbolTokens.keySet()){
                if(symbol.charAt(0) == chr)
                    syms.add(symbol);
            }
            Collections.sort(syms);//sort symbols ascending
            boolean match = true;
            for(String s : syms){ //look ahead to find longest matching token
                match = true;
                for(int i = 0; i < s.length(); i++){
                    if((index + i - 1) < length)
                        match = match && text.charAt(index+i-1) == s.charAt(i);
                    else match = false;
                }
                if(match) token = s; //store matched token
            }
            if(token.length() > 0) break; //if match found, stop there
            else junk += "" + chr; //otherwise character is a junk
        }
        if(junk.length() > 0){ //return junk if exist
            index = idx + junk.length();
            token = junk;
            return JUNK;
        }
        //return match
        index = idx + token.length();
        int sym = symbolTokens.get(token);
        if(sym == -100){ //handle comment
            index = text.length();
            return EOLN;
        }
        return sym;
    }

    public static void test(){
        String labels[]={ "IF", "HALT", "READ", "WRITE", "BEGIN", "END",
        "EOLN",
        "LT", "LTE", "GT", "GTE", "EQU", "NEQ", "GOTO", "SETEQ", "COLON",
        "PLUS", "MINUS", "TIMES", "DIVIDE", "LBRAK", "RBRAK", "JUNK",
          "IDENT", "NUMBER"};
        String testStrings[]={"< = <= begin end ():>>=",
         "if halt read write == != !", "+-*/\\678", "2<3=(x12g12)",
        "   !==kvo443:Ffptr@#  $ L p- // &# ^((%^&*()+)goto(|:][5%if.   "};
        Lex l=new Lex();
        int token;
        for(int j=0; j<testStrings.length;j++){
          l.set(testStrings[j]);
          System.out.println(testStrings[j]);
          token=l.next();
          while(token!=l.EOLN){
            System.out.print("{"+labels[token]+", '"+l.str()+"'} ");
            token=l.next();
          }
          System.out.println('\n'+"-----------------");
        }
       System.out.println("Expect 666: "+l.next()+l.next()+l.next());
    }
}
