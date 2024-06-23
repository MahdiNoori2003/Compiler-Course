package main.visitor.nameAnalyzer;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.FunctionDeclaration;
import main.ast.nodes.declaration.MainDeclaration;
import main.ast.nodes.declaration.PatternDeclaration;
import main.ast.nodes.declaration.VarDeclaration;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.value.FunctionPointer;
import main.ast.nodes.expression.value.ListValue;
import main.ast.nodes.statement.*;
import main.compileError.CompileError;
import main.compileError.nameErrors.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.ItemAlreadyExists;
import main.symbolTable.exceptions.ItemNotFound;
import main.symbolTable.item.FunctionItem;
import main.symbolTable.item.PatternItem;
import main.symbolTable.item.VarItem;
import main.visitor.Visitor;

import java.util.ArrayList;

public class NameAnalyzer extends Visitor<Void> {
    public ArrayList<CompileError> nameErrors = new ArrayList<>();

    @Override
    public Void visit(Program program) {
        SymbolTable.root = new SymbolTable();
        SymbolTable.top = new SymbolTable();

        
        int duplicateFunctionId = 0;
        ArrayList<FunctionItem> functionItems = new ArrayList<>();
        for (FunctionDeclaration functionDeclaration : program.getFunctionDeclarations()) {
            FunctionItem functionItem = new FunctionItem(functionDeclaration);
            try {
                SymbolTable.root.put(functionItem);
                functionItems.add(functionItem);
            } catch (ItemAlreadyExists e) {
                nameErrors.add(new RedefinitionOfFunction(functionDeclaration.getLine(),
                        functionDeclaration.getFunctionName().getName()));
                duplicateFunctionId += 1;
                String freshName = functionItem.getName() + "#" + String.valueOf(duplicateFunctionId);
                Identifier newId = functionDeclaration.getFunctionName();
                newId.setName(freshName);
                functionDeclaration.setFunctionName(newId);
                FunctionItem newItem = new FunctionItem(functionDeclaration);
                functionItems.add(newItem);
                try {
                    SymbolTable.root.put(newItem);
                } catch (ItemAlreadyExists ignored) {
                }
            }
        }

        //addPatterns
        int duplicatePatternId = 0;
        ArrayList<PatternItem> patternItems = new ArrayList<>();
        for (PatternDeclaration patternDeclaration : program.getPatternDeclarations()) {
            PatternItem patternItem = new PatternItem(patternDeclaration);
            try {
                SymbolTable.root.put(patternItem);
                patternItems.add(patternItem);
            } catch (ItemAlreadyExists e) {
                nameErrors.add(new RedefinitionOfPattern(patternDeclaration.getLine(),
                        patternDeclaration.getPatternName().getName()));
                duplicatePatternId += 1;
                String freshName = patternItem.getName() + "#" + String.valueOf(duplicatePatternId);
                Identifier newId = patternDeclaration.getPatternName();
                newId.setName(freshName);
                patternDeclaration.setPatternName(newId);
                PatternItem newItem = new PatternItem(patternDeclaration);
                patternItems.add(newItem);
                try {
                    SymbolTable.root.put(newItem);
                } catch (ItemAlreadyExists ignored) {
                }
            }
        }
        int visitingFunctionIndex = 0;
        for (FunctionDeclaration functionDeclaration : program.getFunctionDeclarations()) {
            FunctionItem functionItem = functionItems.get(visitingFunctionIndex);
            SymbolTable functionSymbolTable = new SymbolTable();
            functionItem.setFunctionSymbolTable(functionSymbolTable);
            SymbolTable.push(functionSymbolTable);
            functionDeclaration.accept(this);
            SymbolTable.pop();
            visitingFunctionIndex += 1;
        }

        //visitPatterns
        int visitingPatternIndex = 0;
        for (PatternDeclaration patternDeclaration : program.getPatternDeclarations()) {
            PatternItem patternItem = patternItems.get(visitingPatternIndex);
            SymbolTable patternSymbolTable = new SymbolTable();
            patternItem.setPatternSymbolTable(patternSymbolTable);
            SymbolTable.push(patternSymbolTable);
            patternDeclaration.accept(this);
            SymbolTable.pop();
            visitingPatternIndex += 1;
        }
        //visitMain
        program.getMain().accept(this);
        return null;
    }

    @Override
    public Void visit(Identifier identifier){
        try {
            SymbolTable.top.getItem("VAR:" + identifier.getName());
        } catch (ItemNotFound e) {
            nameErrors.add(new VariableNotDeclared(identifier.getLine(),
                    identifier.getName()));
        }
        return null;
    }

    @Override
    public Void visit(VarDeclaration varDeclaration){
        VarItem varItem = new VarItem(varDeclaration.getName());
        try {
            SymbolTable.top.put(varItem);
        } catch (ItemAlreadyExists e) { 
            nameErrors.add(new DuplicateArg(varDeclaration.getLine(),
                    varDeclaration.getName().getName()));
        }
        return null;
    }

    @Override
    public Void visit(FunctionDeclaration functionDeclaration){
        for (VarDeclaration varDec : functionDeclaration.getArgs()) {
            if(varDec.getName().getName().equals(functionDeclaration.getFunctionName().getName().split("#")[0])){
                nameErrors.add(new IdenticalArgFunctionName(functionDeclaration.getLine(),
                        functionDeclaration.getFunctionName().getName()));
            }
            varDec.accept(this);
        }

        for (Statement stmt : functionDeclaration.getBody()) {
            stmt.accept(this);
        }

        return null;
    }

    @Override
    public Void visit(PatternDeclaration patternDeclaration){
        VarItem varItem = new VarItem(patternDeclaration.getTargetVariable());
        try {
            SymbolTable.top.put(varItem);
        } catch (ItemAlreadyExists e) {}
        if(patternDeclaration.getTargetVariable().getName().equals(patternDeclaration.getPatternName().getName().split("#")[0])){
            nameErrors.add(new IdenticalArgPatternName(patternDeclaration.getLine(),
                    patternDeclaration.getPatternName().getName()));
        }
        for(Expression cond : patternDeclaration.getConditions()) {
            cond.accept(this);
        }

        for(Expression returnExp : patternDeclaration.getReturnExp()) {
            returnExp.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(MainDeclaration mainDeclaration){
        for (Statement stmt : mainDeclaration.getBody()) {
            stmt.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(ReturnStatement returnStatement){
        if (returnStatement.hasRetExpression()){
            returnStatement.getReturnExp().accept(this);
        }
        return null;
    }

    @Override
    public Void visit(IfStatement ifStatement){
        for(Expression cond : ifStatement.getConditions()){
            cond.accept(this);
        }


        SymbolTable dummySymbolTable = SymbolTable.top.makeSymbolTableSnapshot();
        SymbolTable.push(dummySymbolTable);
        for(Statement thenStmt : ifStatement.getThenBody()){
            thenStmt.accept(this);
        }
        SymbolTable.pop();
        dummySymbolTable = SymbolTable.top.makeSymbolTableSnapshot();
        SymbolTable.push(dummySymbolTable);
        for(Statement elseStmt : ifStatement.getElseBody()){
            elseStmt.accept(this);
        }
        SymbolTable.pop();

        return null;
    }

    @Override
    public Void visit(PutStatement putStatement){
        putStatement.getExpression().accept(this);
        return null;
    }

    @Override
    public Void visit(LenStatement lenStatement){
        lenStatement.getExpression().accept(this);
        return null;
    }

    @Override
    public Void visit(PushStatement pushStatement){
        pushStatement.getInitial().accept(this);
        pushStatement.getToBeAdded().accept(this);
        return null;
    }

    @Override
    public Void visit(LoopDoStatement loopDoStatement){ 

        SymbolTable dummySymbolTable = SymbolTable.top.makeSymbolTableSnapshot();
        SymbolTable.push(dummySymbolTable);
        for(Expression cond : loopDoStatement.getLoopConditions()){
            cond.accept(this);
        }
        for(Statement stmt : loopDoStatement.getLoopBodyStmts()){
            stmt.accept(this);
        }
        if (loopDoStatement.getLoopRetStmt() != null)
            loopDoStatement.getLoopRetStmt().accept(this);
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(ForStatement forStatement){
        
        for(Expression range : forStatement.getRangeExpressions()){
            range.accept(this);
        }

        SymbolTable dummySymbolTable = SymbolTable.top.makeSymbolTableSnapshot();
        SymbolTable.push(dummySymbolTable);
        try {
            SymbolTable.top.put(new VarItem(forStatement.getIteratorId()));
        } catch (ItemAlreadyExists e) {}

        for(Expression cond : forStatement.getLoopBodyExpressions()){
            cond.accept(this);
        }
        for(Statement stmt : forStatement.getLoopBody()){
            stmt.accept(this);
        }

        if (forStatement.getReturnStatement() != null){
            forStatement.getReturnStatement().accept(this);
        }

        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(MatchPatternStatement matchPatternStatement){
        matchPatternStatement.getMatchArgument().accept(this);
        return null;
    }

    @Override
    public Void visit(ChopStatement chopStatement){
        chopStatement.getChopExpression().accept(this);
        return null;
    }

    @Override
    public Void visit(ChompStatement chompStatement){
        chompStatement.getChompExpression().accept(this);
        return null;
    }

    @Override
    public Void visit(AssignStatement assignStatement){

        if(assignStatement.isAccessList())
            assignStatement.getAccessListExpression().accept(this);
        assignStatement.getAssignExpression().accept(this);
        if (assignStatement.getAssignOperator() == AssignOperator.ASSIGN){
            VarItem varItem = new VarItem(assignStatement.getAssignedId());
            try {
                SymbolTable.top.put(varItem);
            } catch (ItemAlreadyExists e){}
        }
        else {
            assignStatement.getAssignedId().accept(this);
        }

        return null;
    }

    @Override
    public Void visit(ExpressionStatement expressionStatement){
        expressionStatement.getExpression().accept(this);
        return null;
    }

    @Override
    public Void visit(AppendExpression appendExpression){
        appendExpression.getAppendee().accept(this);
        for(Expression appended : appendExpression.getAppendeds()){
            appended.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(BinaryExpression binaryExpression){
        binaryExpression.getFirstOperand().accept(this);
        binaryExpression.getSecondOperand().accept(this);
        return null;
    }

    @Override
    public Void visit(UnaryExpression unaryExpression){
        unaryExpression.getExpression().accept(this);
        return null;
    }

    @Override
    public Void visit(AccessExpression accessExpression){
        if(accessExpression.isFunctionCall() && accessExpression.getAccessedExpression() instanceof Identifier id){
            try {
                if(SymbolTable.root.getItem("Function:" + id.getName()) instanceof FunctionItem functionItem){
                    int defaultArgCounter = 0;
                    for(VarDeclaration arg : functionItem.getFunctionDeclaration().getArgs()){
                        if(arg.getDefaultVal() != null) defaultArgCounter++;
                    }
                    int differenceOfArgs = functionItem.getFunctionDeclaration().getArgs().size() -
                                                                accessExpression.getArguments().size();
                    if(differenceOfArgs > defaultArgCounter || differenceOfArgs < 0){
                        nameErrors.add(new ArgMisMatch(accessExpression.getLine(), functionItem.getName()));
                    }
                }
                else{
                    nameErrors.add(new FunctionNotDeclared(accessExpression.getLine(),
                        id.getName()));
                }
            } catch (ItemNotFound e) {
                nameErrors.add(new FunctionNotDeclared(accessExpression.getLine(),
                        id.getName()));
            }
            
        }
        else if (accessExpression.isFunctionCall() && accessExpression.getAccessedExpression() instanceof LambdaExpression lambdaExpression){
            int defaultArgCounter = 0;
            for(VarDeclaration arg : lambdaExpression.getDeclarationArgs()){
                if(arg.getDefaultVal() != null) defaultArgCounter++;
            }
            int differenceOfArgs = lambdaExpression.getDeclarationArgs().size() -
                                                        accessExpression.getArguments().size();
            if(differenceOfArgs > defaultArgCounter || differenceOfArgs < 0){
                nameErrors.add(new ArgMisMatch(accessExpression.getLine(), "lambda"));
            }
        }
        else if(!accessExpression.isFunctionCall() && accessExpression.getAccessedExpression() instanceof Identifier id){
            id.accept(this);
        }

        for(Expression arg : accessExpression.getArguments()){
            arg.accept(this);
        }

        for(Expression dimAccess : accessExpression.getDimentionalAccess()){
            dimAccess.accept(this);
        }
        return null;
    }

    @Override
    public Void visit(LambdaExpression lambdaExpression){
        SymbolTable functionSymbolTable = SymbolTable.top.makeSymbolTableSnapshot();
        SymbolTable.push(functionSymbolTable);
        for(VarDeclaration arg : lambdaExpression.getDeclarationArgs()){
            arg.accept(this);
        }
        for(Statement stm : lambdaExpression.getBody()){
            stm.accept(this);
        }
        SymbolTable.pop();
        return null;
    }

    @Override
    public Void visit(ListValue listValue){
        for(Expression exp : listValue.getElements()){
            exp.accept(this);
        }
        return null;
    }
    @Override
    public Void visit(FunctionPointer functionPointer){
        try{
            if(!(SymbolTable.root.getItem("Function:" + functionPointer.getId().getName()) instanceof FunctionItem)){
                nameErrors.add(new FunctionNotDeclared(functionPointer.getLine(),
                        functionPointer.getId().getName()));
            }
        }
        catch(ItemNotFound e){
            nameErrors.add(new FunctionNotDeclared(functionPointer.getLine(),
                        functionPointer.getId().getName()));
        }
        return null;
    }

}