package main.visitor.codeGenerator;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.FunctionDeclaration;
import main.ast.nodes.declaration.MainDeclaration;
import main.ast.nodes.declaration.VarDeclaration;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.BinaryOperator;
import main.ast.nodes.expression.value.FunctionPointer;
import main.ast.nodes.expression.value.ListValue;
import main.ast.nodes.expression.value.primitive.BoolValue;
import main.ast.nodes.expression.value.primitive.IntValue;
import main.ast.nodes.expression.value.primitive.StringValue;
import main.ast.nodes.statement.*;
import main.ast.type.FptrType;
import main.ast.type.ListType;
import main.ast.type.NoType;
import main.ast.type.Type;
import main.ast.type.primitiveType.BoolType;
import main.ast.type.primitiveType.IntType;
import main.ast.type.primitiveType.StringType;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemAlreadyExists;
import main.symbolTable.exceptions.ItemNotFound;
import main.symbolTable.item.FunctionItem;
import main.symbolTable.item.VarItem;
import main.visitor.Visitor;
import main.visitor.type.TypeChecker;
import org.stringtemplate.v4.ST;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class CodeGenerator extends Visitor<String> {
    private final String outputPath;
    private FileWriter mainFile;
    private final TypeChecker typeChecker;
    private final Set<String> visited;
    private final ArrayList<String> endPoints = new ArrayList<String>();
    private final ArrayList<String> startPoints = new ArrayList<String>();
    private FunctionItem curFunction;
    private final HashMap<String, Integer> slots = new HashMap<>();
    private int curLabel = 0;

    public CodeGenerator(TypeChecker typeChecker){
        this.typeChecker = typeChecker;
        this.visited = typeChecker.visited;
        outputPath = "./codeGenOutput/";
        prepareOutputFolder();
    }
    private int slotOf(String var) {
        if (!slots.containsKey(var)) {
            slots.put(var, slots.size());
            return slots.size() - 1;
        }
        return slots.get(var);
    }
    public String getFreshLabel(){
        String fresh = "Label_" + curLabel;
        curLabel++;
        return fresh;
    }
    public String getType(Type element){
        String type = "";
        switch (element){
            case StringType stringType -> type += "Ljava/lang/String;";
            case IntType intType -> type += "Ljava/lang/Integer;";
            case FptrType fptrType -> type += "LFptr;";
            case ListType listType -> type += "Ljava/util/ArrayList;";
            case BoolType boolType -> type += "Ljava/lang/Boolean;";
            case null, default -> {
                type += "V";
            }
        }
        return type;
    }
    public String getSignatureType(Type element){
        String type = "";
        switch (element){
            case StringType stringType -> type += "Ljava/lang/String;";
            case IntType intType -> type += "I";
            case FptrType fptrType -> type += "LFptr;";
            case ListType listType -> type += "Ljava/util/ArrayList;";
            case BoolType boolType -> type += "Z";
            case null, default -> {
                type += "V";
            }
        }
        return type;
    }
    public String getClass(Type element){
        String className = "";
        switch (element){
            case StringType stringType -> className += "java/lang/String";
            case IntType intType -> className += "java/lang/Integer";
            case BoolType boolType -> className += "java/lang/Boolean";
            case null -> className += "java/lang/Object";
            default -> {}
        }
        return className;
    }

    private void prepareOutputFolder(){
        String jasminPath = "utilities/jarFiles/jasmin.jar";
        String listClassPath = "utilities/codeGenerationUtilityClasses/List.j";
        String fptrClassPath = "utilities/codeGenerationUtilityClasses/Fptr.j";
        try{
            File directory = new File(this.outputPath);
            File[] files = directory.listFiles();
            if(files != null)
                for (File file : files)
                    file.delete();
            directory.mkdir();
        }
        catch(SecurityException e){
            // ignore
        }
        copyFile(jasminPath, this.outputPath + "jasmin.jar");
        copyFile(listClassPath, this.outputPath + "List.j");
        copyFile(fptrClassPath, this.outputPath + "Fptr.j");

        try {
            String path = outputPath + "Main.j";
            File file = new File(path);
            file.createNewFile();
            mainFile = new FileWriter(path);
        } catch (IOException e){
            // ignore
        }
    }
    private void copyFile(String toBeCopied, String toBePasted){
        try {
            File readingFile = new File(toBeCopied);
            File writingFile = new File(toBePasted);
            InputStream readingFileStream = new FileInputStream(readingFile);
            OutputStream writingFileStream = new FileOutputStream(writingFile);
            byte[] buffer = new byte[1024];
            int readLength;
            while ((readLength = readingFileStream.read(buffer)) > 0)
                writingFileStream.write(buffer, 0, readLength);
            readingFileStream.close();
            writingFileStream.close();
        } catch (IOException e){
            // ignore
        }
    }
    private void addCommand(String command){
        try {
            command = String.join("\n\t\t", command.split("\n"));
            if(command.startsWith("Label_"))
                mainFile.write("\t" + command + "\n");
            else if(command.startsWith("."))
                mainFile.write(command + "\n");
            else
                mainFile.write("\t\t" + command + "\n");
            mainFile.flush();
        } catch (IOException e){
            // ignore
        }
    }
    private void handleMainClass(){
        String commands = """
                .method public static main([Ljava/lang/String;)V
                .limit stack 128
                .limit locals 128
                new Main
                invokespecial Main/<init>()V
                return
                .end method
                """;
        addCommand(commands);
    }
    @Override
    public String visit(Program program){
        String commands = """
                .class public Main
                .super java/lang/Object
                """;
        addCommand(commands);
        handleMainClass();

        for(String funcName : this.visited) {
            try {
                this.curFunction = (FunctionItem) SymbolTable.root.getItem(FunctionItem.START_KEY +
                        funcName);
                this.curFunction.getFunctionDeclaration().accept(this);
            } catch(ItemNotFound ignored) {}
        }

        program.getMain().accept(this);
        return null;
    }
    @Override
    public String visit(FunctionDeclaration functionDeclaration){
        slots.clear();
        SymbolTable.push(new SymbolTable());
        String commands = "";
        String args = "(";
        for (int i=0; i<this.curFunction.getArgumentTypes().size(); i++) {
            args += getSignatureType(this.curFunction.getArgumentTypes().get(i));
            slotOf(functionDeclaration.getArgs().get(i).getName().getName());
            VarItem newVarItem = new VarItem(functionDeclaration.getArgs().get(i).getName());
            newVarItem.setType(this.curFunction.getArgumentTypes().get(i));
            try {
                SymbolTable.top.put(newVarItem);
            }catch (ItemAlreadyExists ignored){
                VarItem item = null;
                try {
                    item = (VarItem) SymbolTable.top.getItem(VarItem.START_KEY + newVarItem.getName());
                } catch (ItemNotFound ignored1) {}
                assert item != null;
                item.setType(this.curFunction.getArgumentTypes().get(i));
            }
        }
        args += ")";
        String returnType = getSignatureType(this.curFunction.getReturnType());
        commands += ".method public static " + functionDeclaration.getFunctionName().getName();
        commands += args + returnType + "\n";
        commands += ".limit stack 128\n";
        commands += ".limit locals 128\n";
        boolean hasReturn = false;
        for(Statement bodyElement:functionDeclaration.getBody()) {
            commands += bodyElement.accept(this) + "\n";
            if (bodyElement instanceof ReturnStatement){
                hasReturn = true;
            }
        }
        if (!hasReturn) {
            commands += "return\n";
        }
        commands += ".end method\n";
        addCommand(commands);
        SymbolTable.pop();
        return null;
    }
    @Override
    public String visit(MainDeclaration mainDeclaration){
        slots.clear();

        String commands = "";
        commands += ".method public <init>()V\n";
        commands += ".limit stack 128\n";
        commands += ".limit locals 128\n";
        commands += "aload_0\n";
        commands += "invokespecial java/lang/Object/<init>()V\n";
        for (var statement : mainDeclaration.getBody())
            commands += statement.accept(this) + "\n";
        commands += "return\n";
        commands += ".end method\n";

        addCommand(commands);
        return null;
    }
    public String visit(AccessExpression accessExpression){
        List<String> commands = new ArrayList<String>();

        if (accessExpression.isFunctionCall()) {
            Identifier functionName = (Identifier)accessExpression.getAccessedExpression();
            String funcName = "";
            Type funcType = functionName.accept(typeChecker);
            FunctionItem functionItem = null;
            if (funcType instanceof FptrType fptr){
                funcName += fptr.getFunctionName();
            }
            else {
                funcName += functionName.getName();
            }
            try {
                functionItem = (FunctionItem) SymbolTable.root.getItem(FunctionItem.START_KEY +
                        funcName);
            } catch(ItemNotFound ignored) {}

            int argCount = 0;
            String args = "(";
            for (Expression arg : accessExpression.getArguments()){
                Type argType = arg.accept(typeChecker);
                args += getSignatureType(argType);
                commands.add(arg.accept(this));
                argCount++;
            }
            for (int i = argCount; i < functionItem.getArgumentTypes().size(); i++){
                args += getSignatureType(functionItem.getArgumentTypes().get(i));
                commands.add(functionItem.getFunctionDeclaration().getArgs().get(i).getDefaultVal().accept(this));
            }
            args += ")";

            String returnType = "";
            returnType += getSignatureType(functionItem.getReturnType());
            commands.add("invokestatic Main/" + funcName + args + returnType);
            return String.join("\n", commands);

        }
        else {
            commands.add(accessExpression.getAccessedExpression().accept(this));
            for (Expression expression : accessExpression.getDimentionalAccess()){
                commands.add(expression.accept(this));
            }
            ListType type = (ListType)accessExpression.getAccessedExpression().accept(typeChecker);
            commands.add("invokevirtual java/util/ArrayList/get(I)Ljava/lang/Object;");
            commands.add("checkcast " + getClass(type.getType()));
            if (type.getType() instanceof IntType)
                commands.add("invokevirtual java/lang/Integer/intValue()I");
            else if (type.getType() instanceof BoolType){
                commands.add("invokevirtual java/lang/Boolean/booleanValue()Z");
            }
            return String.join("\n", commands);
        }
        //TODO
    }

    @Override
    public String visit(AssignStatement assignStatement){
        List<String> commands = new ArrayList<>();
        Type assignValueType = assignStatement.getAssignExpression().accept(typeChecker);

        if(assignStatement.isAccessList()) {
            commands.add(assignStatement.getAssignedId().accept(this));
            commands.add(assignStatement.getAccessListExpression().accept(this));

            switch (assignStatement.getAssignOperator()){
                case AssignOperator.ASSIGN -> {
                    commands.add(assignStatement.getAssignExpression().accept(this));
                    if (assignValueType instanceof IntType)
                        commands.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                    else if (assignValueType instanceof BoolType){
                        commands.add("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
                    }
                    commands.add("checkcast " + getClass(null));
                    commands.add("invokevirtual java/util/ArrayList/set(ILjava/lang/Object;)Ljava/lang/Object;");
                }
                case AssignOperator.PLUS_ASSIGN -> {
                    commands.add(assignStatement.getAssignedId().accept(this));
                    commands.add(assignStatement.getAccessListExpression().accept(this));
                    commands.add("invokevirtual java/util/ArrayList/get(I)Ljava/lang/Object;");
                    commands.add("checkcast " + getClass(new IntType()));
                    commands.add("invokevirtual java/lang/Integer/intValue()I");
                    commands.add(assignStatement.getAssignExpression().accept(this));
                    commands.add("iadd");
                    commands.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                    commands.add("checkcast " + getClass(null));
                    commands.add("invokevirtual java/util/ArrayList/set(ILjava/lang/Object;)Ljava/lang/Object;");
                }
                case AssignOperator.MINUS_ASSIGN -> {
                    commands.add(assignStatement.getAssignedId().accept(this));
                    commands.add(assignStatement.getAccessListExpression().accept(this));
                    commands.add("invokevirtual java/util/ArrayList/get(I)Ljava/lang/Object;");
                    commands.add("checkcast " + getClass(new IntType()));
                    commands.add("invokevirtual java/lang/Integer/intValue()I");
                    commands.add(assignStatement.getAssignExpression().accept(this));
                    commands.add("ineg");
                    commands.add("iadd");
                    commands.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                    commands.add("checkcast " + getClass(null));
                    commands.add("invokevirtual java/util/ArrayList/set(ILjava/lang/Object;)Ljava/lang/Object;");
                }
                case AssignOperator.DIVIDE_ASSIGN -> {
                    commands.add(assignStatement.getAssignedId().accept(this));
                    commands.add(assignStatement.getAccessListExpression().accept(this));
                    commands.add("invokevirtual java/util/ArrayList/get(I)Ljava/lang/Object;");
                    commands.add("checkcast " + getClass(new IntType()));
                    commands.add("invokevirtual java/lang/Integer/intValue()I");
                    commands.add(assignStatement.getAssignExpression().accept(this));
                    commands.add("idiv");
                    commands.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                    commands.add("checkcast " + getClass(null));
                    commands.add("invokevirtual java/util/ArrayList/set(ILjava/lang/Object;)Ljava/lang/Object;");
                }
                case AssignOperator.MULT_ASSIGN -> {
                    commands.add(assignStatement.getAssignedId().accept(this));
                    commands.add(assignStatement.getAccessListExpression().accept(this));
                    commands.add("invokevirtual java/util/ArrayList/get(I)Ljava/lang/Object;");
                    commands.add("checkcast " + getClass(new IntType()));
                    commands.add("invokevirtual java/lang/Integer/intValue()I");
                    commands.add(assignStatement.getAssignExpression().accept(this));
                    commands.add("imul");
                    commands.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                    commands.add("checkcast " + getClass(null));
                    commands.add("invokevirtual java/util/ArrayList/set(ILjava/lang/Object;)Ljava/lang/Object;");
                }
                case AssignOperator.MOD_ASSIGN -> {
                    commands.add(assignStatement.getAssignedId().accept(this));
                    commands.add(assignStatement.getAccessListExpression().accept(this));
                    commands.add("invokevirtual java/util/ArrayList/get(I)Ljava/lang/Object;");
                    commands.add("checkcast " + getClass(new IntType()));
                    commands.add("invokevirtual java/lang/Integer/intValue()I");
                    commands.add(assignStatement.getAssignExpression().accept(this));
                    commands.add("irem");
                    commands.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
                    commands.add("checkcast " + getClass(null));
                    commands.add("invokevirtual java/util/ArrayList/set(ILjava/lang/Object;)Ljava/lang/Object;");
                }
                case null, default -> {
                }
            }

        }
        else {

            commands.add(assignStatement.getAssignExpression().accept(this));
            switch (assignStatement.getAssignOperator()){
                case AssignOperator.ASSIGN -> {
                    VarItem newVarItem = new VarItem(assignStatement.getAssignedId());
                    newVarItem.setType(assignValueType);
                    try {
                        SymbolTable.top.put(newVarItem);
                    }catch (ItemAlreadyExists ignored){
                        VarItem item = null;
                        try {
                            item = (VarItem) SymbolTable.top.getItem(VarItem.START_KEY + newVarItem.getName());
                        } catch (ItemNotFound ignored1) {}
                        assert item != null;
                        item.setType(assignValueType);
                    }
                    commands.add((assignValueType instanceof IntType || assignValueType instanceof BoolType ? "istore " : "astore ")
                            + slotOf(assignStatement.getAssignedId().getName()));

                }
                case AssignOperator.PLUS_ASSIGN -> {
                    commands.add("iload " + slotOf(assignStatement.getAssignedId().getName()));
                    commands.add("iadd");
                    commands.add("istore " + slotOf(assignStatement.getAssignedId().getName()));
                }
                case AssignOperator.MINUS_ASSIGN -> {
                    commands.add("ineg");
                    commands.add("iload " + slotOf(assignStatement.getAssignedId().getName()));
                    commands.add("iadd");
                    commands.add("istore " + slotOf(assignStatement.getAssignedId().getName()));
                }
                case AssignOperator.DIVIDE_ASSIGN -> {
                    commands.add("iload " + slotOf(assignStatement.getAssignedId().getName()));
                    commands.add("swap");
                    commands.add("idiv");
                    commands.add("istore " + slotOf(assignStatement.getAssignedId().getName()));
                }
                case AssignOperator.MULT_ASSIGN -> {
                    commands.add("iload " + slotOf(assignStatement.getAssignedId().getName()));
                    commands.add("imul");
                    commands.add("istore " + slotOf(assignStatement.getAssignedId().getName()));
                }
                case AssignOperator.MOD_ASSIGN -> {
                    commands.add("iload " + slotOf(assignStatement.getAssignedId().getName()));
                    commands.add("swap");
                    commands.add("irem");
                    commands.add("istore " + slotOf(assignStatement.getAssignedId().getName()));
                }
                case null, default -> {
                }
            }
        }
        return String.join("\n", commands);
    }
    @Override
    public String visit(IfStatement ifStatement){
        //TODO
        ArrayList<String> commands = new ArrayList<>();
        for (Expression condition : ifStatement.getConditions()){
            commands.add(condition.accept(this));
        }
        String thenLabel = getFreshLabel();
        String elseLabel = getFreshLabel();
        String exitLabel = getFreshLabel();

        commands.add("ifeq" + " " + elseLabel);
        commands.add(thenLabel + ":");

        SymbolTable.push(SymbolTable.top.copy());
        for (Statement statement : ifStatement.getThenBody())
            commands.add(statement.accept(this));
        SymbolTable.pop();
        commands.add("goto " + exitLabel);
        commands.add(elseLabel + ":");
        if (!ifStatement.getElseBody().isEmpty()) {
            SymbolTable.push(SymbolTable.top.copy());
            for (Statement statement : ifStatement.getElseBody())
                commands.add(statement.accept(this));
            SymbolTable.pop();
        }

        commands.add(exitLabel + ":");
        return String.join("\n",commands);
    }
    @Override
    public String visit(PutStatement putStatement){
        List<String> commands = new ArrayList<>();

        commands.add("getstatic java/lang/System/out Ljava/io/PrintStream;");
        commands.add(putStatement.getExpression().accept(this));
        Type type = putStatement.getExpression().accept(typeChecker);
        if (type instanceof IntType || type instanceof BoolType)
            commands.add("invokevirtual java/io/PrintStream/println(I)V");
        else if (type instanceof StringType)
            commands.add("invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V");
        return String.join("\n", commands);
    }
    @Override
    public String visit(ReturnStatement returnStatement){
        List<String> commands = new ArrayList<>();

        Expression returnExpr = returnStatement.getReturnExp();
        if(returnExpr == null) {
            commands.add("return");
            return String.join("\n",commands);

        }
        Type type = returnExpr.accept(typeChecker);
        if(type instanceof NoType) {
            commands.add("return");
        }
        else {
            commands.add(returnExpr.accept(this));
            if (type instanceof IntType || type instanceof BoolType)
                commands.add("ireturn");
            else
                commands.add("areturn");
        }

        return String.join("\n",commands);
    }
    @Override
    public String visit(ExpressionStatement expressionStatement){
        return expressionStatement.getExpression().accept(this);
    }
    @Override
    public String visit(BinaryExpression binaryExpression){
        ArrayList<String> commands = new ArrayList<>();
        commands.add(binaryExpression.getFirstOperand().accept(this));
        commands.add(binaryExpression.getSecondOperand().accept(this));
        Type firstOperandType = binaryExpression.getFirstOperand().accept(typeChecker);

        String enterLabel;
        String exitLabel;
        if (binaryExpression.getOperator() != BinaryOperator.PLUS && binaryExpression.getOperator() != BinaryOperator.MINUS
                && binaryExpression.getOperator() != BinaryOperator.MULT && binaryExpression.getOperator() != BinaryOperator.DIVIDE )
        {
            enterLabel = getFreshLabel();
            exitLabel = getFreshLabel();

            switch (binaryExpression.getOperator()) {
                case BinaryOperator.EQUAL -> {
                    if (firstOperandType instanceof IntType || firstOperandType instanceof BoolType)
                    {
                        commands.add("if_icmpeq " + enterLabel);
                    }
                    else
                    {
                        commands.add("if_acmpeq " + enterLabel);
                    }
                }
                case BinaryOperator.NOT_EQUAL -> {
                    if (firstOperandType instanceof IntType || firstOperandType instanceof BoolType)
                    {
                        commands.add("if_icmpne " + enterLabel);
                    }
                    else
                    {
                        commands.add("if_acmpne " + enterLabel);
                    }
                }
                case BinaryOperator.GREATER_THAN -> commands.add("if_icmpgt " + enterLabel);
                case BinaryOperator.LESS_THAN -> commands.add("if_icmplt " + enterLabel);
                case BinaryOperator.GREATER_EQUAL_THAN -> commands.add("if_icmpge " + enterLabel);
                case BinaryOperator.LESS_EQUAL_THAN -> commands.add("if_icmple " + enterLabel);
                default -> {}
            }

            commands.add("ldc 0");
            commands.add("goto " + exitLabel);
            commands.add(enterLabel + ":");
            commands.add("ldc 1");
            commands.add(exitLabel + ":");
        }
        else {
            switch (binaryExpression.getOperator()) {
                case BinaryOperator.PLUS -> commands.add("iadd");
                case BinaryOperator.MINUS -> commands.add("isub");
                case BinaryOperator.MULT -> commands.add("imul");
                case BinaryOperator.DIVIDE -> commands.add("idiv");
                default -> {}
            }
        }
        return String.join("\n", commands);
    }
    @Override
    public String visit(UnaryExpression unaryExpression){
        List<String> commands = new ArrayList<>();
        commands.add(unaryExpression.getExpression().accept(this));
        switch (unaryExpression.getOperator()) {
            case MINUS -> {
                commands.add("ineg");
            }
            case NOT -> {
                commands.add("ldc 1");
                commands.add("ixor");
            }
            case INC -> {
                commands.add("ldc 1");
                commands.add("iadd");
                if (unaryExpression.getExpression() instanceof Identifier identifier)
                    commands.add("istore " + slotOf(identifier.getName()));
            }
            case DEC -> {
                commands.add("ldc -1");
                commands.add("iadd");
                if (unaryExpression.getExpression() instanceof Identifier identifier)
                    commands.add("istore " + slotOf(identifier.getName()));
            }
        }
        return String.join("\n", commands);
    }
    @Override
    public String visit(Identifier identifier){
        String command = "aload";
        Type type = identifier.accept(typeChecker);
        if (type instanceof IntType || type instanceof BoolType)
            command = "iload";

        else if(type instanceof FptrType fptr){
            String commands = "";
            commands += "new Fptr\n";
            commands += "dup\n";
            commands += "aload_0\n";
            commands += "ldc " + "\"" + fptr.getFunctionName() + "\"\n";
            commands += "invokespecial Fptr/<init>(Ljava/lang/Object;Ljava/lang/String;)V\n";
            return commands;
        }

        return command + " " + slotOf(identifier.getName());
    }
    @Override
    public String visit(LoopDoStatement loopDoStatement){
        List<String> commands = new ArrayList<String>();
        String startLabel =  getFreshLabel();
        String endLabel =  getFreshLabel();
        endPoints.add(endLabel);
        startPoints.add(startLabel);
        commands.add(startLabel + ":");
        SymbolTable.push(SymbolTable.top.copy());
        for (Statement statement : loopDoStatement.getLoopBodyStmts()){
            commands.add(statement.accept(this));
        }
        SymbolTable.pop();
        commands.add("goto " + startLabel);
        commands.add(endLabel + ":");
        endPoints.removeLast();
        startPoints.removeLast();
        return String.join("\n", commands);
    }
    @Override
    public String visit(BreakStatement breakStatement){
        List<String> commands = new ArrayList<String>();
        commands.add("goto " + endPoints.getLast());
        return String.join("\n", commands);
    }
    @Override
    public String visit(NextStatement nextStatement){
        List<String> commands = new ArrayList<String>();
        commands.add("goto " + startPoints.getLast());
        return String.join("\n", commands);
    }
    @Override
    public String visit(LenStatement lenStatement){
        List<String> commands = new ArrayList<>();
        commands.add(lenStatement.getExpression().accept(this));
        if(lenStatement.getExpression().accept(typeChecker) instanceof ListType){
            commands.add("invokevirtual java/util/ArrayList/size()I");
        }
        else{
            commands.add("invokevirtual java/lang/String/length()I");
        }
        return String.join("\n", commands);
    }
    @Override
    public String visit(ChopStatement chopStatement){
        List<String> commands = new ArrayList<>();
        commands.add(chopStatement.getChopExpression().accept(this));
        commands.add("dup");
        commands.add("invokevirtual java/lang/String/length()I");
        commands.add("ldc -1");
        commands.add("iadd");
        commands.add("ldc 0");
        commands.add("swap");
        commands.add("invokevirtual java/lang/String/substring(II)Ljava/lang/String;");
        return String.join("\n", commands);
    }
    @Override
    public String visit(FunctionPointer functionPointer){
        FptrType fptr = (FptrType) functionPointer.accept(typeChecker);
        String commands = "";
        commands += "new Fptr\n";
        commands += "dup\n";
        commands += "aload_0\n";
        commands += "ldc " + "\"" + fptr.getFunctionName() + "\"\n";
        commands += "invokespecial Fptr/<init>(Ljava/lang/Object;Ljava/lang/String;)V\n";
        return commands;
    }

    @Override
    public String visit(ListValue listValue){
        List<String> commands = new ArrayList<>();
        commands.add("new java/util/ArrayList");
        commands.add("dup");
        commands.add("invokespecial java/util/ArrayList/<init>()V");
        commands.add("astore " + slotOf("_array_"));
        for (Expression expression : listValue.getElements()){
            commands.add("aload " + slotOf("_array_"));
            commands.add(expression.accept(this));
            Type type = expression.accept(typeChecker);
            if (type instanceof IntType)
                commands.add("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;");
            else if (type instanceof BoolType){
                commands.add("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;");
            }
            commands.add("invokevirtual java/util/ArrayList/add(Ljava/lang/Object;)Z");
            commands.add("pop");
        }
        commands.add("aload " + slotOf("_array_"));

        return String.join("\n", commands);
    }
    @Override
    public String visit(IntValue intValue){
        List<String> commands = new ArrayList<>();
        commands.add("ldc " + intValue.getIntVal());
        return String.join("\n", commands);
    }
    @Override
    public String visit(BoolValue boolValue){
        List<String> commands = new ArrayList<>();
        commands.add("ldc " + (boolValue.getBool() ? 1 : 0));
        return String.join("\n", commands);
    }
    @Override
    public String visit(StringValue stringValue){
        String commands = "";
        commands += ("ldc " + stringValue.getStr());
        return commands;
    }
}