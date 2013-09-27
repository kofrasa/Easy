import java.util.Scanner;
import java.io.*;
/*  Class Simpletron implements an emulator for the machine described below
 * 
 *  Specifications of the Simpletron Machine (taken from Deitel & Deitel,
   C++: How to program).

   Memory:   1000 locations, numbered 0, 1, ...,999, each containing a word
             consisting of a signed five digit integer

   A single Accumulator

   When a word is interpreted as an instruction, it must be positive,
   the first two digits must be one of the operations below, and the
   third, fourth, and fifth digits give the memory location to which the
   operation refers.

   Operations:

           10       Read from "input" to given memory location
           11       Write to "output" from given memory location
           20       Load data from given memory location into accumulator
           21       Store contents of accumulator into given memory location
           30       Add contents of given memory location to accumulator
           31       Subtract contents of given memory location from
                    accumulator
           32       Divide accumulator by contents of given memory location
           33       Multiply accumulator by contents of given memory location
           40       Branch to given memory location
           41       Branch to given memory location if accumulator is negative
           42       Branch to given memory location if accumulator is zero
           43       Halt execution

    USING MY EMULATOR OF THE SIMPLETRON

    The emulator is located in the file Simpletron.java

    The call to the file takes the form
          java Simpletron <input>  [-v]
    where <input> is the input file and -v is an optional command that leads
    to more verbose output.

    The input file for sml should consist of lines of code, followed by END,
    followed, possibly, by lines of input data.  Each line of code or data
    should contain an integer of up to 5 digits. Anything on a line after the
    integer is ignored (a good place to put comments).  Here is a very simple
    program (it reads and displays 6).
*/
class Simpletron{
  private int memory[]=new int[1000],  //for storing instructions and data
    PC,     //The program counter, used for storing the memory location of the
            // current instructions
    accum;   // for storing the results of computations
  private boolean verbose;   // provide verbose output when the emulator executes a 
           // program if and only if verbose is true
  Scanner sc;  // for reading the input from a file containing a program to run
               // on the emulator
  /*Give names to each operator */
  public static final int READ=10, WRITE=11,LOAD=20,STORE=21,ADD=30,SUB=31,DIV=32,
    MUL=33,JUMP=40,JUMPL=41,JUMPZ=42,HALT=43;
  
  /*Postconditions: verbose set. The scanner sc set to head of file whose name is
   * given in input. (Exit if file not found). The contents of the file, Simpletron
   * instructions, plus data, loaded into memory. Get Scanner sc past the 'END'
   * in the file (Exit if no END in file). All entries in memory between -99999 nad
   * 99999
   */
  public Simpletron(String input,boolean verb){
    verbose=verb;
    try{
      sc=new Scanner(new FileInputStream(input));
    }
    catch(FileNotFoundException f){
      System.err.println("The file '"+input+"' cannot be opened");
      System.exit(1);
    }
    PC=0;
    System.out.println("LOADING...");
    while(PC<1000 && sc.hasNextInt()){
      memory[PC]=sc.nextInt();
      System.out.println(PC+": "+memory[PC]);
      assert memory[PC]>=-99999 && memory[PC]<=99999 : "Number("+memory[PC]+
        ") is out of range [-99999, 99999]";
      sc.nextLine();
      PC++;
    }
    check(sc.hasNext() && sc.next().equals("END")," 'END' expected");
    sc.nextLine();
  }
  
  public static void main(String arg[]){
    check(arg!=null && (arg.length==1 || arg.length==2 && arg[1].equals("-v")),
      " Usage: Simpletron <input> <output> [-v]");
    Simpletron s=new Simpletron(arg[0],arg.length==2);
    s.run();
  }
  
  /* Emulate the fetch-execute cycle in the Simpletron: get instruction, decode,
   *   execute instruction, increment PC. 
   * Precondition: memory loaded with Simpletron program and data
   * Postcondition:   0<=PC<=10000 && memory[PC-1]==HALT
   */
  private void run(){
    PC=0;
    accum=0;
    while(PC<=9999 && op()!=HALT){
      details();
      switch(op()){
        case READ: doRead(); break;
        case WRITE: doWrite(); break;
        case LOAD: accum=memory[address()]; break;
        case STORE: memory[address()]=accum; break;
        case ADD:  accum+=memory[address()]; checkRange(); break;
        case SUB: accum-=memory[address()]; checkRange(); break;
        case MUL: accum*=memory[address()]; checkRange(); break;
        case DIV: check(memory[address()]!=0, "Division by zero");
                  accum/=memory[address()]; 
                  break;
        case JUMP: PC=address()-1; break;
        case JUMPL: if(accum<0) PC=address()-1; break;
        case JUMPZ: if(accum==0) PC=address()-1; break;
        default:  System.err.println("Bad op code: "+op() + ", PC= "+PC);
                  System.exit(1);
      }
      PC++;
    }
    check(PC<10001,"Execution went off top of memory");
  }
  
  /*Be sure that we have data in the accumulator in range.*/
  void checkRange(){
    check(accum>=-99999 && accum<=99999, "Accumulator overflow or underflow");
  }
  
  /*  OP==READ
   *  Read int from file and store in memory at location ADDRESS
   */
  private void doRead(){
    check(sc.hasNextInt(), "Failure to read: int expected");
    memory[address()]=sc.nextInt();
    check(memory[address()]>=-99999 && memory[address()]<=9999,
      "(doRead()) number out of range [-99999,99999]");
    System.out.println("             <==== "+memory[address()]);
  }
  
  private void doWrite(){
    System.out.println("     ====> "+memory[address()]);
  }
  
  /* memory[PC]=xxyyy. Return xx. */
  private int op(){
    return memory[PC]/1000;
  }
  
  /* memory[PC]=xxyyy. Return yyy */
  private int address(){
    if(memory[PC]<0)
      return (-memory[PC])%1000;
    return memory[PC]%1000;
  }
  
  /*If verbose, then print out memory location, value of PC, and
   * value of accum 
   */
  private void details(){
    int codes[]={10,11,20,21,30,31,32,33,40,41,42,43};
    String ops[]={"READ", "WRITE", "LOAD","STORE","ADD","SUB","DIV","MUL",
      "JUMP","JUMPL","JUMPZ","HALT"};
    String lhs;
    int target;
    if(verbose){
      target=op();
      lhs="NO-OP";
      for(int j=0;j<codes.length;j++)
        if(codes[j]==target)
           lhs=ops[j];
      System.out.println("PC: "+PC+", val="+memory[PC]+", code: ["+lhs+" "+address()+
                         ", accum: "+accum+']');
    }
  }
  
  /* If b is false then detect error, display message, and quite */
  private static void check(boolean b,String mess){
    if(!b){
      System.err.println("Error: "+mess);
      System.exit(2);
    }
  }
}