/*
 * @author Francis <fasante@ashesi.edu.gh>
 */

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

/*
 * Performs syntatic and semantic analysis of the EASY language and then generates the associated Simpletron machine code
 */
public class Parse {

    /*Give names to each operator */
	public static final int READ=10, WRITE=11,LOAD=20,STORE=21,ADD=30,SUB=31,DIV=32,
		MUL=33,JUMP=40,JUMPL=41,JUMPZ=42,HALT=43;
  
    private String line; //current line of source text
    private String temp; //string of previous token
    private int token; //current token
    private Lex lexer; //Lexical Analyzer
    private Map<String, Integer> vars; //variables    
    private Map<String, Integer> labels; //trap labels here
    private ArrayList<String> constants;
    private LineNumberReader rd; //input reader
    private BufferedWriter wr; //output writer
    private BufferedWriter asm; //writer for generated code
    private boolean finished; //end of program reached
    private boolean secondParse; // second parsing
    private int pc, sp, jmp; //program counter, stack pointer, jump address

    public Parse(){
        lexer = new Lex();
        vars = new HashMap<String, Integer>();
        labels = new HashMap<String, Integer>();
        constants = new ArrayList<String>();
    }

    public void parse(String source, String output, String code) {
        try {
            wr = new BufferedWriter(new FileWriter(output));            
            rd = new LineNumberReader(new FileReader(source));

            //first pass
            wr.write(String.format("First pass...\n"));
            start(false);             
            wr.write(String.format("\n----------------------\n"));

            //allocate space
            wr.write("Addresses of variables and constants\n");
            alloc();            
            wr.write("\n--------------\n");

            //second pass
            wr.write(String.format("\nSecond pass...\n"));
            rd = new LineNumberReader(new FileReader(source));
            asm = new BufferedWriter(new FileWriter(code));
            start(true);
        }
        catch(FileNotFoundException ex){
            System.err.println(ex.getMessage());
            terminate(1);
        }
        catch (IOException ex) {
            System.err.println(ex.getMessage());
            terminate(1);
        }
    }

    private void start(boolean secondParse){
        if(!secondParse){
            vars.clear();
            labels.clear();
            constants.clear();
        }
        sp = 1000; //always enter an expression pointing to the top
        pc = 0;
        jmp = 0;
        this.secondParse = secondParse;
        finished = false;
        token = Lex.EOLN;
        nextToken();
        processProgram();
    }

    /*
     * allocate space for variables and constants.
     * stores addresses in vars map
     */
    private void alloc(){
        try{            
            ArrayList<String> names = new ArrayList<String>(vars.keySet());
            Collections.sort(names);			
            for(String s : names){
                vars.put(s, pc);
                wr.write(String.format("%s: %d\n", s, pc++));
            }
			Collections.sort(constants);
            for(String s : constants){
                vars.put(s, pc);
                wr.write(String.format("%s: %d\n", s, pc++));
            }
        }
        catch (IOException ex){}
    }

    private void processProgram() {        
        processDeclarations();
        do{
            if(check(Lex.IDENT) && lexer.peek() == Lex.COLON){
                accept(Lex.IDENT);                
                if(!secondParse){
                    if(labels.containsKey(temp)) //check for label declarations
                        error("Label " +temp+ " has already been declared");                    
                    labels.put(temp, pc); //also record the position of the jump instruction
                }             
                accept(Lex.COLON);                
            }
            processLine();
            expect(Lex.EOLN, "Newline expected after statement.");
        }
        while(!check(Lex.END));

        finished = true;
        accept(Lex.END);
        if(!check(Lex.EOLN)) error("Newline expected after \"end\" statement.");
        success();
    }

    private void processDeclarations(){
        while(accept(Lex.EOLN)) {} //handle newlines
        while(check(Lex.IDENT)){ //handle variables
            if(!secondParse){
                if(vars.containsKey(lexer.str())) //check if variable is declared
                    error("Variable "+lexer.str()+" is already declared in line "+vars.get(lexer.str())+".");
                vars.put(lexer.str(), rd.getLineNumber());
            }
            accept(Lex.IDENT);
            expect(Lex.EOLN, "Newline expected after variable declaration.");
            
            while(accept(Lex.EOLN)){}
        }
        expect(Lex.BEGIN, "Begin statement expected.");
        /*
         * This line corrects an anomaly in the EASY language BNF.
         * This is due to a newline both at the end of the "Declarations" and "Line".
         * This requires that there be at least a space between the begin and end statements for a corrent program with no "Lines".
         * To solve this, the newline at the end of the begin statement is not consumed but rather checked if present.
         */
        if(!check(Lex.EOLN)) error("Newline expected after \"begin\" statement.");
    }    

    private void processExpression(){
        processTerm();
        while(check(Lex.PLUS) || check(Lex.MINUS)){
            int op = token;
            if(accept(Lex.PLUS) || accept(Lex.MINUS));
            processTerm();

            gen(LOAD, ++sp);
            if(op == Lex.PLUS) gen(ADD, sp-1);
            else gen(SUB, sp-1);
            gen(STORE, sp);
        }
    }

    private void processTerm(){
        processFactor();
        while(check(Lex.TIMES) || check(Lex.DIVIDE)){
            int op = token;
            if(accept(Lex.TIMES) || accept(Lex.DIVIDE));
            processFactor();

            gen(LOAD, ++sp);
            if(op == Lex.DIVIDE) gen(DIV, sp-1);
            else gen(MUL, sp-1);
            gen(STORE, sp);
        }
    }

    private void processFactor(){
        if(check(Lex.NUMBER)){
            accept(Lex.NUMBER);
            //add constant if not in list
            if(!secondParse && !constants.contains(temp)) constants.add(temp);
            gen(LOAD, addrVal(temp));
            gen(STORE, --sp);            
        }
        else if(check(Lex.IDENT)) {            
            requireIDENT();
            gen(LOAD, addrVal(temp));
            gen(STORE, --sp);
        }
        else {
            expect(Lex.LBRAK, "Left parentheses \"(\" expected.");
            processExpression();
            expect(Lex.RBRAK, "Right parentheses \")\" expected.");
        }
    }    

    private void processLine(){        
        if(check(Lex.IDENT)){            
            requireIDENT();
            String x = temp;
            expect(Lex.SETEQ, "Assignment operator \"=\" expected.");
            processExpression();
            gen(LOAD, sp++);
            gen(STORE, addrVal(x));
        }
        else if(accept(Lex.IF)){
            processExpression();
			//first check
            if(check(Lex.LT) || check(Lex.LTE) || check(Lex.GT) || check(Lex.GTE) || check(Lex.EQU) || check(Lex.NEQ)){
                int op = token;
				//now accept
                if(accept(Lex.LT) || accept(Lex.LTE) || accept(Lex.GT) || accept(Lex.GTE) || accept(Lex.EQU) || accept(Lex.NEQ));
                processExpression();
                expect(Lex.GOTO, "Goto statement expected.");
                requireLABEL();
				
                jmp = addrGoto(temp); //jump address
                genCond(op);
            }
            else error("Invalid comparison operator.");
        }
        else if(accept(Lex.GOTO)){
            requireLABEL();
            gen(JUMP, addrGoto(temp));
        }        
        else if(accept(Lex.READ)){
            requireIDENT();
            gen(READ, addrVal(temp));
        }
        else if(accept(Lex.WRITE)){
            processExpression();
            gen(WRITE, sp++);
        }
        else if(accept(Lex.HALT)){
            gen(HALT, 0);
        }
    }

    /*
     * expects an identifier subject to semantic conditions
     */
    private void requireIDENT(){
        if(!secondParse){
            if(!vars.containsKey(lexer.str()))
                error("Undeclared variable "+lexer.str()+".");
        }
        expect(Lex.IDENT, "Variable expected.");
    }

    /*
     * expects a label
     */
    private void requireLABEL(){
        if(check(Lex.IDENT) && secondParse){
            if(!labels.containsKey(lexer.str()))
                error("Undeclared label after goto statement.");
        }
        expect(Lex.IDENT, "Identifier expected after \"goto\" statement.");
    }

    /*
     * generates a Simpletron instruction
     */
    private void gen(int opcode, int address) {
        try {
            if(secondParse) {
                int code = opcode * 1000 + address;
                wr.write(String.format("\t\t[%d] %d\n", pc, code));
                asm.write(String.format("%d\n", code));
            }
            ++pc;
        }
        catch (IOException ex) {            
        }
    }

    //generate ==
    private void genEQU(){
        gen(LOAD, ++sp);
        gen(SUB, sp-1);
        gen(JUMPZ, jmp);
        sp++;
    }

    //generate <
    private void genLT(){
        gen(LOAD, ++sp);
        gen(SUB, sp-1);
        gen(JUMPL, jmp);
        sp++;
    }

    //generate <=
    private void genLTE(){
        genEQU();
        gen(JUMPL, jmp);
    }

    //generate >
    private void genGT(){
        gen(LOAD, sp++);
        gen(SUB, sp++);
        gen(JUMPL, jmp);
    }

    //generate >=
    private void genGTE(){
        genEQU();
        sp -= 2; //restore stack pointer
        genGT();
    }

    //generate !=
    private void genNEQ(){
        genLT();
        gen(JUMPZ, pc+2);
        gen(JUMP, jmp);
    }

    /*
     * generates code for IF condition experession
     */
    private void genCond(int op) {
        switch(op){
            case Lex.LT: genLT(); break;
            case Lex.GT: genGT(); break;
            case Lex.LTE: genLTE(); break;
            case Lex.GTE: genGTE(); break;
            case Lex.EQU: genEQU(); break;
            case Lex.NEQ: genNEQ(); break;
        }
    }

    private int addrVal(String value){
        if(secondParse) return vars.get(value);
        return 0;
    }

    private int addrGoto(String label){
        if(secondParse) return labels.get(label);
        return 0;
    }    

    /*
     * Returns the next token from the source file
     */
    private void nextToken(){
        if(token == Lex.EOLN){              
            if(readLine());
            else if(!finished) error("Invalid program termination.");
        }
        token = lexer.next();
    }

    /*
     * Reads a line of input into the Lexer and print it
     */
    private boolean readLine(){
        try {
            if((line = rd.readLine()) != null){
                lexer.set(line);
                wr.write(rd.getLineNumber() + ". " + line);
                wr.newLine();
                return true;
            }
        }
        catch(IOException ex){
            System.err.print(ex.getMessage());            
        }
        return false;
    }

    /*
     * consumes the expected lexeme if it matches the current token, and then progress to the next token.
     */
    private boolean accept(int lexeme){
        if(token == lexeme){
            temp = lexer.str();
            nextToken();
            return true;
        }
        return false;
    }

    /*
     * accepts a lexeme or report error on failure
     */
    private boolean expect(int lexeme, String error){
        if(accept(lexeme)) return true;
        else error(error);
        return false;
    }

    /*
     * checks the current token against the expected lexeme
     */
    private boolean check(int lexeme){
        return token == lexeme;
    }

    /*
     * terminates the parsing process and release resources
     * @param int status 0 => success, 1 => error
     */
    private void terminate(int status){
        try{            
            rd.close();
            if(status == 1 || secondParse){
                wr.flush();
                wr.close();
                if(secondParse){
                    asm.flush();
                    asm.close();
                }
            }
        }
        catch(IOException ex){
            System.err.print(ex.getMessage());            
        }
        if(status == 1){ //stop execution on error
            System.exit(0);
        }
    }

    /*
     * terminates program on success
     */
    private void success(){
        if(secondParse){
            ArrayList<String> values =  new ArrayList<String>(vars.keySet());            
            values.removeAll(constants);
            Collections.sort(values);
            int size = values.size();
            for(int i=0; i < size; ++i) gen(0, 0); //initialize variables

            values.addAll(constants);
            values.retainAll(constants);
            Collections.sort(values);
            size = values.size();
            for(int i =0; i < size; ++i) gen(0, Integer.parseInt(values.get(i))); //initialize literals
            try{
                asm.write("END\n"); //end of program
                while((line = rd.readLine()) != null) asm.write(line+"\n"); //read data and append to the end of instructions
            } catch(IOException ex){}
        }        
        
        System.out.println(String.format("Successful Parse...%d",(secondParse? 2 : 1)));
        terminate(0);
    }

    /*
     * terminates program on error
     */
    private void error(String message){
        try {
            wr.write("\nError: " + message);
            System.out.println("Error: " + message);
            System.out.println("Line: " + rd.getLineNumber());            
        }
        catch (IOException ex) {            
        }
        finally{
            terminate(1);
        }
    }
	
    public static void main(String[] args){
        if(args.length != 3){
            System.out.println("Usage: Parse <source> <output> <code>");
            System.exit(0);
        }
        Parse p = new Parse();
        p.parse(args[0], args[1], args[2]);
//        for(int i = 1; i < 7; ++i){
//            p.parse("zip/s"+i+".txt", "zip/out"+i+".txt", "zip/code"+i+".txt");
//        }
//        System.out.println("finish");
    }
}
