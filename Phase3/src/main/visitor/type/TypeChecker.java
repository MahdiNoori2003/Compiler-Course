package main.visitor.type;

import main.ast.nodes.Program;
import main.ast.nodes.declaration.*;
import main.ast.nodes.expression.*;
import main.ast.nodes.expression.operators.*;
import main.ast.nodes.expression.value.*;
import main.ast.nodes.expression.value.primitive.*;
import main.ast.nodes.statement.*;
import main.ast.type.*;
import main.ast.type.primitiveType.*;
import main.compileError.CompileError;
import main.compileError.typeErrors.*;
import main.symbolTable.SymbolTable;
import main.symbolTable.exceptions.*;
import main.symbolTable.item.*;
import main.visitor.Visitor;

import java.util.*;

public class TypeChecker extends Visitor<Type> {
    public ArrayList<CompileError> typeErrors = new ArrayList<>();
    private Stack<HashSet<Type>> functionTypesSymbolTable = new Stack<HashSet<Type>>();
    @Override
    public Type visit(Program program){
        SymbolTable.root = new SymbolTable();
        SymbolTable.top = new SymbolTable();
        for(FunctionDeclaration functionDeclaration : program.getFunctionDeclarations()){
            FunctionItem functionItem = new FunctionItem(functionDeclaration);
            try {
                SymbolTable.root.put(functionItem);
            }catch (ItemAlreadyExists ignored){}
        }
        for(PatternDeclaration patternDeclaration : program.getPatternDeclarations()){
            PatternItem patternItem = new PatternItem(patternDeclaration);
            try{
                SymbolTable.root.put(patternItem);
            }catch (ItemAlreadyExists ignored){}
        }
        functionTypesSymbolTable.push(new HashSet<Type>());
        program.getMain().accept(this);
        functionTypesSymbolTable.pop();
        return null;
    }

    @Override
    public Type visit(FunctionDeclaration functionDeclaration){
        SymbolTable.push(new SymbolTable());
        try {
            FunctionItem functionItem = (FunctionItem) SymbolTable.root.getItem(FunctionItem.START_KEY +
                    functionDeclaration.getFunctionName().getName());
            ArrayList<Type> currentArgTypes = functionItem.getArgumentTypes();
            for (int i = 0; i < functionDeclaration.getArgs().size(); i++) {
                VarItem argItem = new VarItem(functionDeclaration.getArgs().get(i).getName());
                if (i < currentArgTypes.size())
                    argItem.setType(currentArgTypes.get(i));
                else
                    argItem.setType(functionDeclaration.getArgs().get(i).getDefaultVal().accept(this));

                try {
                    SymbolTable.top.put(argItem);
                }catch (ItemAlreadyExists ignored){}
            }
        }catch (ItemNotFound ignored){}

        functionTypesSymbolTable.push(new HashSet<>());
        for(Statement statement : functionDeclaration.getBody()){
            statement.accept(this);
        }

        HashSet<Type> currFunctionTypes = functionTypesSymbolTable.pop();
        SymbolTable.pop();
        Type returnType = new NoType();
        if (currFunctionTypes.size() > 1){
            typeErrors.add(new FunctionIncompatibleReturnTypes(functionDeclaration.getLine(),
                    functionDeclaration.getFunctionName().getName()));
        }
        else if(currFunctionTypes.size() == 1) {
            returnType = currFunctionTypes.stream().findFirst().get();
        }
        return returnType;
    }
    @Override
    public Type visit(PatternDeclaration patternDeclaration){
        SymbolTable.push(new SymbolTable());
        HashSet<Type> returnTypes = new HashSet<>();

        try {
            PatternItem patternItem = (PatternItem) SymbolTable.root.getItem(PatternItem.START_KEY +
                    patternDeclaration.getPatternName().getName());
            VarItem varItem = new VarItem(patternDeclaration.getTargetVariable());
            varItem.setType(patternItem.getTargetVarType());
            try {
                SymbolTable.top.put(varItem);
            }catch (ItemAlreadyExists ignored){}
            for(Expression expression : patternDeclaration.getConditions()){
                if(!(expression.accept(this) instanceof BoolType)){
                    typeErrors.add(new ConditionIsNotBool(expression.getLine()));
                    SymbolTable.pop();
                    return new NoType();
                }
            }
            for(Expression statement : patternDeclaration.getReturnExp()){
                Type exprType = statement.accept(this);
                returnTypes.add(exprType);
            }
                
            if (returnTypes.size() > 1){
                typeErrors.add(new PatternIncompatibleReturnTypes (patternDeclaration.getLine(),
                        patternDeclaration.getPatternName().getName()));
                return new NoType();
            }
        }catch (ItemNotFound ignored){}
        SymbolTable.pop();
        if(returnTypes.isEmpty())
            return new NoType();
        return returnTypes.stream().findFirst().get();
    }

    @Override
    public Type visit(MainDeclaration mainDeclaration){
        for(Statement statement : mainDeclaration.getBody()){
            statement.accept(this);
        }
        return null;
    }

    @Override
    public Type visit(AccessExpression accessExpression){
        if(accessExpression.isFunctionCall()){
            if (accessExpression.getAccessedExpression() instanceof Identifier id) {
                try {
                    ArrayList<Type> argumentTypes = new ArrayList<Type>();
                    FunctionItem functionItem = (FunctionItem) SymbolTable.root
                                    .getItem(FunctionItem.START_KEY + id.getName());
                    for (Expression arg:accessExpression.getArguments()){
                        argumentTypes.add(arg.accept(this));
                    }
                    functionItem.setArgumentTypes(argumentTypes);
                    return functionItem.getFunctionDeclaration().accept(this);
                } catch (ItemNotFound ignored) {
                    try {
                        VarItem varItem = (VarItem) SymbolTable.top
                                .getItem(VarItem.START_KEY + id.getName());
                        if(varItem.getType() instanceof FptrType fptr){
                            ArrayList<Type> argumentTypes = new ArrayList<Type>();
                            FunctionItem functionItem = (FunctionItem) SymbolTable.root
                                    .getItem(FunctionItem.START_KEY + fptr.getFunctionName());
                            for (Expression arg:accessExpression.getArguments()){
                                argumentTypes.add(arg.accept(this));
                            }
                            functionItem.setArgumentTypes(argumentTypes);
                            return functionItem.getFunctionDeclaration().accept(this);
                        }
                        return varItem.getType();
                    }catch (ItemNotFound e){}
                }

            }
        }
        else{
            Type accessedType = accessExpression.getAccessedExpression().accept(this);
            if(!(accessedType instanceof StringType) && !(accessedType instanceof ListType)){
                typeErrors.add(new IsNotIndexable(accessExpression.getLine()));
                return new NoType();
            }
            for (Expression expression : accessExpression.getDimentionalAccess()){
                if (!expression.accept(this).sameType(new IntType())){
                    typeErrors.add(new AccessIndexIsNotInt(accessExpression.getLine()));
                }
            }

            if (accessedType.sameType(new StringType())){
                return accessedType;
            }
            else {
                return ((ListType)accessedType).getType();
            }
        }
        return new NoType();
    }

    @Override
    public Type visit(ReturnStatement returnStatement){
        if (returnStatement.hasRetExpression()) {
            Type retType = returnStatement.getReturnExp().accept(this);
            HashSet<Type> currFunctionTypes = functionTypesSymbolTable.pop();
            currFunctionTypes.add(retType);

            functionTypesSymbolTable.push(currFunctionTypes);
            return retType;
        }
        return new NoType();
    }

    @Override
    public Type visit(ExpressionStatement expressionStatement){
        return expressionStatement.getExpression().accept(this);
    }

    @Override
    public Type visit(ForStatement forStatement){
        SymbolTable.push(SymbolTable.top.copy());
        Type ranType = forStatement.getRangeExpression().accept(this);
        if (ranType instanceof NoType){
            return new NoType();
        }
        ListType rangeType =  (ListType)ranType ;
        VarItem varItem = new VarItem(forStatement.getIteratorId());
        varItem.setType(rangeType.getType());
        try{
            SymbolTable.top.put(varItem);
        }catch (ItemAlreadyExists ignored){}

        for(Statement statement : forStatement.getLoopBodyStmts())
            statement.accept(this);
        SymbolTable.pop();

        return new NoType();
    }

    @Override
    public Type visit(IfStatement ifStatement){
        SymbolTable.push(SymbolTable.top.copy());
        for(Expression expression : ifStatement.getConditions())
            if(!(expression.accept(this) instanceof BoolType))
                typeErrors.add(new ConditionIsNotBool(expression.getLine()));
        for(Statement statement : ifStatement.getThenBody())
            statement.accept(this);
        for(Statement statement : ifStatement.getElseBody())
            statement.accept(this);
        SymbolTable.pop();

        return new NoType();
    }

    @Override
    public Type visit(LoopDoStatement loopDoStatement){
        SymbolTable.push(SymbolTable.top.copy());
        for(Statement statement : loopDoStatement.getLoopBodyStmts())
            statement.accept(this);
        SymbolTable.pop();
        return new NoType();
    }

    @Override
    public Type visit(AssignStatement assignStatement){
        if(assignStatement.isAccessList()){

            if (!assignStatement.getAccessListExpression().accept(this).sameType(new IntType())){
                typeErrors.add(new AccessIndexIsNotInt(assignStatement.getLine()));
                return new NoType();
            }
        }
        else{
            VarItem newVarItem = new VarItem(assignStatement.getAssignedId());
            newVarItem.setType(assignStatement.getAssignExpression().accept(this));
            if (assignStatement.getAssignOperator() == AssignOperator.ASSIGN){

                try {
                    SymbolTable.top.put(newVarItem);
                }catch (ItemAlreadyExists ignored){}
            }
        }
        return new NoType();
    }

    @Override
    public Type visit(BreakStatement breakStatement){
        for(Expression expression : breakStatement.getConditions())
            if(!((expression.accept(this)) instanceof BoolType))
                typeErrors.add(new ConditionIsNotBool(expression.getLine()));

        return null;
    }

    @Override
    public Type visit(NextStatement nextStatement){
        for(Expression expression : nextStatement.getConditions())
            if(!((expression.accept(this)) instanceof BoolType))
                typeErrors.add(new ConditionIsNotBool(expression.getLine()));

        return null;
    }

    @Override
    public Type visit(PushStatement pushStatement){
        Type initialType = pushStatement.getInitial().accept(this);
        Type addedType = pushStatement.getToBeAdded().accept(this);
        if (!initialType.sameType(new StringType()) && !(initialType instanceof ListType)){
            typeErrors.add(new IsNotPushedable(pushStatement.getLine()));
        }
        else if (initialType.sameType(new StringType()) && !addedType.sameType(new StringType())){
            typeErrors.add(new PushArgumentsTypesMisMatch(pushStatement.getLine()));
        }
        else if (initialType instanceof ListType list) {
            if(!(list.getType() instanceof NoType)){
                if(!list.getType().sameType(addedType)){
                    typeErrors.add(new PushArgumentsTypesMisMatch(pushStatement.getLine()));
                }
            }
            else {
                if(pushStatement.getInitial() instanceof Identifier identifier){
                    try {
                        SymbolTableItem item = SymbolTable.top.getItem("VAR:"+ identifier.getName());
                        if (item instanceof VarItem varItem){
                            varItem.setType(new ListType(addedType));
                        }
                    } catch (ItemNotFound e){}

                }
            }
        }
        return new NoType();
    }

    @Override
    public Type visit(PutStatement putStatement){
        //TODO:visit putStatement
        putStatement.getExpression().accept(this);
        return new NoType();
    }

    @Override
    public Type visit(BoolValue boolValue){
        return new BoolType();
    }

    @Override
    public Type visit(IntValue intValue){
        return new IntType();
    }

    @Override
    public Type visit(FloatValue floatValue){return new FloatType();}

    @Override
    public Type visit(StringValue stringValue){
        return new StringType();
    }

    @Override
    public Type visit(ListValue listValue){
        Type baseType = null;
        if (!listValue.getElements().isEmpty()){
            baseType = listValue.getElements().getFirst().accept(this);
        }
        else {
            return new ListType(new NoType());
        }

        for (Expression expression : listValue.getElements()){
            if (!baseType.sameType(expression.accept(this))){
                typeErrors.add(new ListElementsTypesMisMatch(listValue.getLine()));
                return new NoType();
            }
        }
        return new ListType(baseType);
    }

    @Override
    public Type visit(FunctionPointer functionPointer){
        return new FptrType(functionPointer.getId().getName());
    }

    @Override
    public Type visit(AppendExpression appendExpression){
        Type appendeeType = appendExpression.getAppendee().accept(this);
        if(!(appendeeType instanceof ListType) && !(appendeeType instanceof StringType)){
            typeErrors.add(new IsNotAppendable(appendExpression.getLine()));
            return new NoType();
        }
        return appendeeType;
    }

    @Override
    public Type visit(BinaryExpression binaryExpression){
        Type firstOperandType = binaryExpression.getFirstOperand().accept(this);
        Type secondOperandType = binaryExpression.getSecondOperand().accept(this);
        if (!firstOperandType.sameType(secondOperandType)){
            typeErrors.add(new NonSameOperands(binaryExpression.getLine(),binaryExpression.getOperator()));
            return new NoType();
        }

        if(binaryExpression.getOperator() == BinaryOperator.DIVIDE || binaryExpression.getOperator() == BinaryOperator.MULT ||
                binaryExpression.getOperator() == BinaryOperator.MINUS || binaryExpression.getOperator() == BinaryOperator.PLUS){

            if (!firstOperandType.sameType(new IntType()) && !firstOperandType.sameType(new FloatType())){
                typeErrors.add(new UnsupportedOperandType(binaryExpression.getLine(),binaryExpression.getOperator().toString()));
                return new NoType();
            }

            if (!secondOperandType.sameType(new IntType()) && !secondOperandType.sameType(new FloatType())){
                typeErrors.add(new UnsupportedOperandType(binaryExpression.getLine(),binaryExpression.getOperator().toString()));
                return new NoType();
            }
            return firstOperandType;

        }
        else {
            return new BoolType();
        }
    }

    @Override
    public Type visit(UnaryExpression unaryExpression){
        Type operandType = unaryExpression.getExpression().accept(this);
        if (unaryExpression.getOperator() != UnaryOperator.NOT){
            if (!operandType.sameType(new IntType()) && !operandType.sameType(new FloatType())) {
                typeErrors.add(new UnsupportedOperandType(unaryExpression.getLine(), unaryExpression.getOperator().toString()));
                return new NoType();
            }
        } else if (unaryExpression.getOperator() == UnaryOperator.NOT) {
            if (!operandType.sameType(new BoolType())) {
                typeErrors.add(new UnsupportedOperandType(unaryExpression.getLine(), unaryExpression.getOperator().toString()));
                return new NoType();
            }
        }
        return operandType;
    }

    @Override
    public Type visit(ChompStatement chompStatement){
        if (!(chompStatement.getChompExpression().accept(this) instanceof StringType)) {
            typeErrors.add(new ChompArgumentTypeMisMatch(chompStatement.getLine()));
            return new NoType();
        }
        return new StringType();
    }

    @Override
    public Type visit(ChopStatement chopStatement){
        return new StringType();
    }

    @Override
    public Type visit(Identifier identifier){
        try {
            SymbolTableItem item = SymbolTable.top.getItem("VAR:" + identifier.getName());
            if (item instanceof VarItem varItem){
                return varItem.getType();
            }
            return new NoType();

        } catch (ItemNotFound e) {
            return new NoType();
        }
    }

    @Override
    public Type visit(LenStatement lenStatement){
        Type exprType = lenStatement.getExpression().accept(this);
        if (!exprType.equals(new StringType()) && !(exprType instanceof ListType)){
            typeErrors.add(new LenArgumentTypeMisMatch(lenStatement.getLine()));
            // TODO::SHOULD BE NO_TYPE????
        }
        return new IntType();
    }

    @Override
    public Type visit(MatchPatternStatement matchPatternStatement){
        try{
            PatternItem patternItem = (PatternItem)SymbolTable.root.getItem(PatternItem.START_KEY +
                    matchPatternStatement.getPatternId().getName());
            patternItem.setTargetVarType(matchPatternStatement.getMatchArgument().accept(this));
            return patternItem.getPatternDeclaration().accept(this);
        }catch (ItemNotFound ignored){}
        return new NoType();
    }

    //TODO -> check for return types
    @Override
    public Type visit(RangeExpression rangeExpression){
        RangeType rangeType = rangeExpression.getRangeType();
        Type baseType = null;
        if (!rangeExpression.getRangeExpressions().isEmpty()){
            baseType = rangeExpression.getRangeExpressions().getFirst().accept(this);
        }
        else {
            return new NoType();
        }
        if(rangeType.equals(RangeType.LIST)){
            for (Expression element : rangeExpression.getRangeExpressions()){
                if (!baseType.sameType(element.accept(this))){
                    typeErrors.add(new ListElementsTypesMisMatch(rangeExpression.getLine()));
                    return new NoType();
                }
            }
            return new ListType(baseType);
        }
        else if(rangeType.equals(RangeType.IDENTIFIER)){
            try {
                VarItem varItem = (VarItem) SymbolTable.top.getItem(VarItem.START_KEY +
                        ((Identifier)(rangeExpression.getRangeExpressions().getFirst())).getName());
                if (! (varItem.getType() instanceof ListType)){
                    typeErrors.add(new IsNotIterable(rangeExpression.getLine()));
                    return new NoType();
                }
            } catch (ItemNotFound e) {}

            return rangeExpression.getRangeExpressions().getFirst().accept(this);
        }
        else if (rangeType.equals(RangeType.DOUBLE_DOT)){
            Type startType = rangeExpression.getRangeExpressions().getFirst().accept(this);
            Type endType = rangeExpression.getRangeExpressions().get(1).accept(this);
            if(!startType.sameType(new IntType()) || !endType.sameType(new IntType())){
                return new NoType();
            }
            return new ListType(new IntType());
        }
        return new NoType();
    }
}
