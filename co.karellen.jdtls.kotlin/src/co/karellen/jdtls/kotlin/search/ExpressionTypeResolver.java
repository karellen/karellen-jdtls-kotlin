/*
 * Copyright 2026 Karellen, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.karellen.jdtls.kotlin.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import co.karellen.jdtls.kotlin.parser.KotlinParser;
import co.karellen.jdtls.kotlin.parser.KotlinParserBaseVisitor;

/**
 * Expression type resolver that walks ANTLR parse trees to determine the
 * type of Kotlin expressions. Extends {@link KotlinParserBaseVisitor} to
 * visit expression nodes and resolve their types using scope chains and
 * symbol tables.
 *
 * @author Arcadiy Ivanov
 */
public class ExpressionTypeResolver
		extends KotlinParserBaseVisitor<KotlinType> {

	private final ScopeChain scopeChain;
	private final SymbolTable symbolTable;
	private final ImportResolver importResolver;
	private final LambdaTypeResolver lambdaTypeResolver;
	private final OverloadResolver overloadResolver;
	private final SubtypeChecker subtypeChecker;

	public ExpressionTypeResolver(ScopeChain scopeChain,
			SymbolTable symbolTable, ImportResolver importResolver) {
		this(scopeChain, symbolTable, importResolver, null);
	}

	public ExpressionTypeResolver(ScopeChain scopeChain,
			SymbolTable symbolTable, ImportResolver importResolver,
			SubtypeChecker subtypeChecker) {
		this.scopeChain = scopeChain;
		this.symbolTable = symbolTable;
		this.importResolver = importResolver;
		this.subtypeChecker = subtypeChecker;
		this.lambdaTypeResolver = new LambdaTypeResolver(
				symbolTable, importResolver);
		this.overloadResolver = subtypeChecker != null
				? new OverloadResolver(symbolTable, subtypeChecker,
						importResolver)
				: null;
	}

	/**
	 * Public entry point for resolving the type of an arbitrary expression.
	 *
	 * @param ctx the expression context
	 * @return the resolved type, never {@code null}
	 */
	public KotlinType resolve(KotlinParser.ExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		KotlinType result = visit(ctx);
		return result != null ? result : KotlinType.UNKNOWN;
	}

	/**
	 * Resolves the type of the receiver portion of a postfix unary
	 * expression. Processes the primary expression and all suffixes
	 * <em>before</em> the given navigation suffix index, giving the
	 * type of the expression that the navigation suffix operates on.
	 *
	 * <p>For example, in {@code getFoo().bar()}, if {@code navIndex}
	 * is 2 (the {@code .bar} navigation suffix), this resolves the
	 * type of {@code getFoo()} — i.e., suffixes 0 and 1.
	 *
	 * @param ctx      the full postfix unary expression context
	 * @param navIndex the index of the matched navigation suffix
	 * @return the resolved receiver type, never {@code null}
	 */
	public KotlinType resolveReceiverUpTo(
			KotlinParser.PostfixUnaryExpressionContext ctx,
			int navIndex) {
		if (ctx == null || ctx.primaryExpression() == null) {
			return KotlinType.UNKNOWN;
		}
		KotlinType type = visit(ctx.primaryExpression());
		if (type == null) {
			type = KotlinType.UNKNOWN;
		}

		List<KotlinParser.PostfixUnarySuffixContext> suffixes = ctx
				.postfixUnarySuffix();
		if (suffixes == null || suffixes.isEmpty() || navIndex <= 0) {
			return type;
		}

		int limit = Math.min(navIndex, suffixes.size());
		return walkSuffixes(ctx, suffixes, limit, type, false);
	}

	// ---------------------------------------------------------------
	// Expression hierarchy pass-through methods
	// ---------------------------------------------------------------

	@Override
	public KotlinType visitExpression(
			KotlinParser.ExpressionContext ctx) {
		if (ctx == null || ctx.disjunction() == null) {
			return KotlinType.UNKNOWN;
		}
		return visit(ctx.disjunction());
	}

	@Override
	public KotlinType visitDisjunction(
			KotlinParser.DisjunctionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.ConjunctionContext> conjunctions = ctx
				.conjunction();
		if (conjunctions == null || conjunctions.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		if (conjunctions.size() > 1) {
			return KotlinType.BOOLEAN;
		}
		return visit(conjunctions.get(0));
	}

	@Override
	public KotlinType visitConjunction(
			KotlinParser.ConjunctionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.EqualityContext> equalities = ctx.equality();
		if (equalities == null || equalities.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		if (equalities.size() > 1) {
			return KotlinType.BOOLEAN;
		}
		return visit(equalities.get(0));
	}

	@Override
	public KotlinType visitEquality(
			KotlinParser.EqualityContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.ComparisonContext> comparisons = ctx
				.comparison();
		if (comparisons == null || comparisons.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		if (ctx.equalityOperator() != null
				&& !ctx.equalityOperator().isEmpty()) {
			return KotlinType.BOOLEAN;
		}
		return visit(comparisons.get(0));
	}

	@Override
	public KotlinType visitComparison(
			KotlinParser.ComparisonContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.GenericCallLikeComparisonContext> generics = ctx
				.genericCallLikeComparison();
		if (generics == null || generics.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		if (ctx.comparisonOperator() != null
				&& !ctx.comparisonOperator().isEmpty()) {
			return KotlinType.BOOLEAN;
		}
		return visit(generics.get(0));
	}

	@Override
	public KotlinType visitGenericCallLikeComparison(
			KotlinParser.GenericCallLikeComparisonContext ctx) {
		if (ctx == null || ctx.infixOperation() == null) {
			return KotlinType.UNKNOWN;
		}
		return visit(ctx.infixOperation());
	}

	@Override
	public KotlinType visitInfixOperation(
			KotlinParser.InfixOperationContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.ElvisExpressionContext> elvisExprs = ctx
				.elvisExpression();
		if (elvisExprs == null || elvisExprs.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		// "is" check returns Boolean
		if (ctx.isOperator() != null && !ctx.isOperator().isEmpty()) {
			return KotlinType.BOOLEAN;
		}
		// "in" check returns Boolean
		if (ctx.inOperator() != null && !ctx.inOperator().isEmpty()) {
			return KotlinType.BOOLEAN;
		}
		return visit(elvisExprs.get(0));
	}

	@Override
	public KotlinType visitElvisExpression(
			KotlinParser.ElvisExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.InfixFunctionCallContext> calls = ctx
				.infixFunctionCall();
		if (calls == null || calls.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		KotlinType leftType = visit(calls.get(0));
		if (leftType == null) {
			return KotlinType.UNKNOWN;
		}
		// Elvis result: non-nullable version of the left operand
		if (ctx.elvis() != null && !ctx.elvis().isEmpty()) {
			return leftType.withNullable(false);
		}
		return leftType;
	}

	@Override
	public KotlinType visitInfixFunctionCall(
			KotlinParser.InfixFunctionCallContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.RangeExpressionContext> ranges = ctx
				.rangeExpression();
		if (ranges == null || ranges.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		return visit(ranges.get(0));
	}

	@Override
	public KotlinType visitRangeExpression(
			KotlinParser.RangeExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.AdditiveExpressionContext> additives = ctx
				.additiveExpression();
		if (additives == null || additives.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		return visit(additives.get(0));
	}

	@Override
	public KotlinType visitAdditiveExpression(
			KotlinParser.AdditiveExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.MultiplicativeExpressionContext> mults = ctx
				.multiplicativeExpression();
		if (mults == null || mults.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		return visit(mults.get(0));
	}

	@Override
	public KotlinType visitMultiplicativeExpression(
			KotlinParser.MultiplicativeExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.AsExpressionContext> asExprs = ctx
				.asExpression();
		if (asExprs == null || asExprs.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		return visit(asExprs.get(0));
	}

	@Override
	public KotlinType visitAsExpression(
			KotlinParser.AsExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		if (ctx.asOperator() != null && !ctx.asOperator().isEmpty()
				&& ctx.type() != null && !ctx.type().isEmpty()) {
			// Cast expression: resolve the target type
			KotlinParser.TypeContext typeCtx = ctx.type().get(
					ctx.type().size() - 1);
			return resolveTypeContext(typeCtx);
		}
		if (ctx.prefixUnaryExpression() == null) {
			return KotlinType.UNKNOWN;
		}
		return visit(ctx.prefixUnaryExpression());
	}

	@Override
	public KotlinType visitPrefixUnaryExpression(
			KotlinParser.PrefixUnaryExpressionContext ctx) {
		if (ctx == null || ctx.postfixUnaryExpression() == null) {
			return KotlinType.UNKNOWN;
		}
		return visit(ctx.postfixUnaryExpression());
	}

	// ---------------------------------------------------------------
	// Core method: chained expression resolution
	// ---------------------------------------------------------------

	@Override
	public KotlinType visitPostfixUnaryExpression(
			KotlinParser.PostfixUnaryExpressionContext ctx) {
		if (ctx == null || ctx.primaryExpression() == null) {
			return KotlinType.UNKNOWN;
		}
		KotlinType type = visit(ctx.primaryExpression());
		if (type == null) {
			type = KotlinType.UNKNOWN;
		}

		List<KotlinParser.PostfixUnarySuffixContext> suffixes = ctx
				.postfixUnarySuffix();
		if (suffixes == null || suffixes.isEmpty()) {
			return type;
		}

		return walkSuffixes(ctx, suffixes, suffixes.size(), type,
				true);
	}

	private KotlinType walkSuffixes(
			KotlinParser.PostfixUnaryExpressionContext ctx,
			List<KotlinParser.PostfixUnarySuffixContext> suffixes,
			int limit, KotlinType type, boolean trackLambdaScopes) {
		int i = 0;
		while (i < limit) {
			KotlinParser.PostfixUnarySuffixContext suffix = suffixes
					.get(i);

			if (suffix.navigationSuffix() != null) {
				KotlinParser.NavigationSuffixContext navSuffix = suffix
						.navigationSuffix();
				if (navSuffix.simpleIdentifier() != null) {
					String memberName = navSuffix.simpleIdentifier()
							.getText();
					if (i + 1 < limit
							&& suffixes.get(i + 1)
									.callSuffix() != null) {
						KotlinParser.CallSuffixContext callSuffix =
								suffixes.get(i + 1).callSuffix();
						int argCount = countArguments(callSuffix);
						KotlinType prevType = type;
						type = resolveMethodCall(type, memberName,
								argCount, callSuffix);
						if (trackLambdaScopes
								&& callSuffix.annotatedLambda()
										!= null) {
							pushLambdaScope(prevType, memberName,
									callSuffix);
						}
						i += 2;
						continue;
					}
					type = resolveMemberType(type, memberName);
				} else {
					type = KotlinType.UNKNOWN;
				}
			} else if (suffix.callSuffix() != null) {
				KotlinParser.CallSuffixContext callSuffix =
						suffix.callSuffix();
				type = resolveCallReturnType(type, callSuffix);
				if (trackLambdaScopes
						&& callSuffix.annotatedLambda() != null
						&& type != null && !type.isUnknown()) {
					pushLambdaScope(type,
							getPrimaryName(ctx), callSuffix);
				}
			} else if (suffix.indexingSuffix() != null) {
				type = resolveIndexingType(type);
			} else if (suffix.postfixUnaryOperator() != null
					&& suffix.postfixUnaryOperator()
							.EXCL_NO_WS() != null) {
				// Not-null assertion (!!): strip nullable
				if (type != null && type.isNullable()) {
					type = type.withNullable(false);
				}
			}

			i++;
		}

		return type;
	}

	// ---------------------------------------------------------------
	// Primary expressions
	// ---------------------------------------------------------------

	@Override
	public KotlinType visitPrimaryExpression(
			KotlinParser.PrimaryExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		if (ctx.simpleIdentifier() != null) {
			return visit(ctx.simpleIdentifier());
		}
		if (ctx.literalConstant() != null) {
			return visit(ctx.literalConstant());
		}
		if (ctx.stringLiteral() != null) {
			return visit(ctx.stringLiteral());
		}
		if (ctx.thisExpression() != null) {
			return visit(ctx.thisExpression());
		}
		if (ctx.superExpression() != null) {
			return visit(ctx.superExpression());
		}
		if (ctx.parenthesizedExpression() != null) {
			return visit(ctx.parenthesizedExpression());
		}
		if (ctx.ifExpression() != null) {
			return visit(ctx.ifExpression());
		}
		if (ctx.whenExpression() != null) {
			return visit(ctx.whenExpression());
		}
		if (ctx.tryExpression() != null) {
			return visit(ctx.tryExpression());
		}
		if (ctx.collectionLiteral() != null) {
			return visit(ctx.collectionLiteral());
		}
		if (ctx.objectLiteral() != null) {
			return visit(ctx.objectLiteral());
		}
		if (ctx.callableReference() != null) {
			return visit(ctx.callableReference());
		}
		if (ctx.functionLiteral() != null) {
			return KotlinType.UNKNOWN;
		}
		if (ctx.jumpExpression() != null) {
			return KotlinType.UNKNOWN;
		}
		return KotlinType.UNKNOWN;
	}

	@Override
	public KotlinType visitSimpleIdentifier(
			KotlinParser.SimpleIdentifierContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		return scopeChain.resolveType(ctx.getText());
	}

	@Override
	public KotlinType visitLiteralConstant(
			KotlinParser.LiteralConstantContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		if (ctx.BooleanLiteral() != null) {
			return KotlinType.BOOLEAN;
		}
		if (ctx.IntegerLiteral() != null) {
			return KotlinType.INT;
		}
		if (ctx.LongLiteral() != null) {
			return KotlinType.LONG;
		}
		if (ctx.RealLiteral() != null) {
			String text = ctx.RealLiteral().getText();
			if (text.endsWith("f") || text.endsWith("F")) {
				return KotlinType.FLOAT;
			}
			return KotlinType.DOUBLE;
		}
		if (ctx.CharacterLiteral() != null) {
			return KotlinType.CHAR;
		}
		if (ctx.NullLiteral() != null) {
			return KotlinType.UNKNOWN.withNullable(true);
		}
		if (ctx.HexLiteral() != null) {
			return KotlinType.INT;
		}
		if (ctx.BinLiteral() != null) {
			return KotlinType.INT;
		}
		if (ctx.UnsignedLiteral() != null) {
			return KotlinType.INT;
		}
		return KotlinType.UNKNOWN;
	}

	@Override
	public KotlinType visitStringLiteral(
			KotlinParser.StringLiteralContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		return KotlinType.STRING;
	}

	@Override
	public KotlinType visitThisExpression(
			KotlinParser.ThisExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		return scopeChain.resolveType("this");
	}

	@Override
	public KotlinType visitSuperExpression(
			KotlinParser.SuperExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		return scopeChain.resolveType("super");
	}

	@Override
	public KotlinType visitParenthesizedExpression(
			KotlinParser.ParenthesizedExpressionContext ctx) {
		if (ctx == null || ctx.expression() == null) {
			return KotlinType.UNKNOWN;
		}
		return visit(ctx.expression());
	}

	@Override
	public KotlinType visitIfExpression(
			KotlinParser.IfExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.ControlStructureBodyContext> bodies = ctx
				.controlStructureBody();
		if (bodies == null || bodies.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		KotlinType thenType = resolveControlStructureBodyType(
				bodies.get(0));
		if (bodies.size() > 1 && subtypeChecker != null) {
			KotlinType elseType = resolveControlStructureBodyType(
					bodies.get(1));
			return subtypeChecker.commonSupertype(thenType, elseType);
		}
		return thenType;
	}

	@Override
	public KotlinType visitWhenExpression(
			KotlinParser.WhenExpressionContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.WhenEntryContext> entries = ctx.whenEntry();
		if (entries == null || entries.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		KotlinType result = KotlinType.UNKNOWN;
		for (KotlinParser.WhenEntryContext entry : entries) {
			KotlinParser.ControlStructureBodyContext body =
					entry.controlStructureBody();
			if (body != null) {
				KotlinType branchType =
						resolveControlStructureBodyType(body);
				if (subtypeChecker != null) {
					result = subtypeChecker.commonSupertype(
							result, branchType);
				} else {
					result = branchType;
					break;
				}
			}
		}
		return result;
	}

	@Override
	public KotlinType visitTryExpression(
			KotlinParser.TryExpressionContext ctx) {
		if (ctx == null || ctx.block() == null) {
			return KotlinType.UNKNOWN;
		}
		KotlinType result = resolveBlockType(ctx.block());
		if (subtypeChecker != null && ctx.catchBlock() != null) {
			for (KotlinParser.CatchBlockContext catchBlock :
					ctx.catchBlock()) {
				KotlinType catchType =
						resolveBlockType(catchBlock.block());
				result = subtypeChecker.commonSupertype(
						result, catchType);
			}
		}
		return result;
	}

	private KotlinType resolveBlockType(
			KotlinParser.BlockContext block) {
		if (block == null || block.statements() == null) {
			return KotlinType.UNKNOWN;
		}
		List<KotlinParser.StatementContext> stmts =
				block.statements().statement();
		if (stmts == null || stmts.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		KotlinParser.StatementContext lastStmt =
				stmts.get(stmts.size() - 1);
		if (lastStmt.expression() != null) {
			return resolve(lastStmt.expression());
		}
		return KotlinType.UNKNOWN;
	}

	@Override
	public KotlinType visitCollectionLiteral(
			KotlinParser.CollectionLiteralContext ctx) {
		return KotlinType.UNKNOWN;
	}

	@Override
	public KotlinType visitObjectLiteral(
			KotlinParser.ObjectLiteralContext ctx) {
		if (ctx == null || ctx.delegationSpecifiers() == null) {
			return KotlinType.UNKNOWN;
		}
		// Resolve to the first supertype: object : Foo() { } → Foo
		List<KotlinParser.AnnotatedDelegationSpecifierContext> specs =
				ctx.delegationSpecifiers()
						.annotatedDelegationSpecifier();
		if (specs == null || specs.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		KotlinParser.DelegationSpecifierContext spec =
				specs.get(0).delegationSpecifier();
		if (spec == null) {
			return KotlinType.UNKNOWN;
		}
		if (spec.constructorInvocation() != null) {
			String typeName = spec.constructorInvocation()
					.userType().getText();
			return resolveTypeName(typeName);
		}
		if (spec.userType() != null) {
			return resolveTypeName(spec.userType().getText());
		}
		return KotlinType.UNKNOWN;
	}

	@Override
	public KotlinType visitCallableReference(
			KotlinParser.CallableReferenceContext ctx) {
		return KotlinType.UNKNOWN;
	}

	// ---------------------------------------------------------------
	// Helper methods
	// ---------------------------------------------------------------

	private KotlinType resolveIndexingType(KotlinType receiverType) {
		if (receiverType == null || receiverType.isUnknown()) {
			return KotlinType.UNKNOWN;
		}
		return receiverType.getElementType();
	}

	private KotlinType resolveMemberType(KotlinType receiverType,
			String memberName) {
		if (receiverType == null || receiverType.isUnknown()) {
			return KotlinType.UNKNOWN;
		}
		// Look up type in symbol table by Kotlin FQN
		SymbolTable.TypeSymbol typeSym = symbolTable
				.lookupType(receiverType.getFQN());
		if (typeSym == null) {
			// Try Java FQN
			typeSym = symbolTable
					.lookupType(receiverType.getJavaFQN());
		}
		if (typeSym == null) {
			return KotlinType.UNKNOWN;
		}
		// Look for field/property
		for (SymbolTable.FieldSymbol field : typeSym.getFields()) {
			if (memberName.equals(field.getName())) {
				if (field.getTypeName() != null) {
					return resolveTypeName(field.getTypeName());
				}
				return KotlinType.UNKNOWN;
			}
		}
		// Look for method (treat as property accessor via getter pattern)
		return KotlinType.UNKNOWN;
	}

	private KotlinType resolveCallReturnType(KotlinType receiverType,
			KotlinParser.CallSuffixContext callCtx) {
		if (receiverType == null || receiverType.isUnknown()) {
			return KotlinType.UNKNOWN;
		}
		// Check if this is a constructor call (receiverType is a class name)
		SymbolTable.TypeSymbol typeSym = symbolTable
				.lookupType(receiverType.getFQN());
		if (typeSym != null) {
			return receiverType;
		}
		return KotlinType.UNKNOWN;
	}

	private KotlinType resolveMethodCall(KotlinType receiverType,
			String methodName, int argCount,
			KotlinParser.CallSuffixContext callCtx) {
		if (receiverType == null || receiverType.isUnknown()) {
			return KotlinType.UNKNOWN;
		}

		// Try overload resolution if available
		if (overloadResolver != null) {
			CallArguments callArgs =
					resolveCallArguments(callCtx, argCount);
			OverloadResolver.Resolution resolution =
					overloadResolver.resolve(receiverType, methodName,
							callArgs.types(), callArgs.namedArgs());
			if (resolution.isResolved()) {
				String returnType =
						resolution.getMethod().getReturnTypeName();
				if (returnType != null) {
					return resolveTypeName(returnType);
				}
			}
			// If ambiguous, fall through to simple lookup
		}

		SymbolTable.TypeSymbol typeSym = symbolTable
				.lookupType(receiverType.getFQN());
		if (typeSym == null) {
			typeSym = symbolTable
					.lookupType(receiverType.getJavaFQN());
		}
		if (typeSym == null) {
			return KotlinType.UNKNOWN;
		}
		for (SymbolTable.MethodSymbol method : typeSym.getMethods()) {
			if (methodName.equals(method.getName())) {
				String returnType = method.getReturnTypeName();
				if (returnType != null) {
					return resolveTypeName(returnType);
				}
			}
		}
		return KotlinType.UNKNOWN;
	}

	private record CallArguments(List<KotlinType> types,
			List<String> namedArgs) {
	}

	private CallArguments resolveCallArguments(
			KotlinParser.CallSuffixContext callCtx, int argCount) {
		if (callCtx == null || callCtx.valueArguments() == null
				|| callCtx.valueArguments().valueArgument() == null) {
			return new CallArguments(
					Collections.nCopies(argCount, KotlinType.UNKNOWN),
					null);
		}
		List<KotlinParser.ValueArgumentContext> args =
				callCtx.valueArguments().valueArgument();
		List<KotlinType> types = new ArrayList<>(argCount);
		List<String> namedArgs = null;
		for (KotlinParser.ValueArgumentContext arg : args) {
			if (arg.expression() != null) {
				KotlinType argType = resolve(arg.expression());
				types.add(argType != null ? argType
						: KotlinType.UNKNOWN);
			} else {
				types.add(KotlinType.UNKNOWN);
			}
			if (arg.simpleIdentifier() != null) {
				if (namedArgs == null) {
					namedArgs = new ArrayList<>();
				}
				namedArgs.add(arg.simpleIdentifier().getText());
			}
		}
		if (callCtx.annotatedLambda() != null) {
			types.add(KotlinType.UNKNOWN);
		}
		return new CallArguments(types, namedArgs);
	}

	private KotlinType resolveTypeName(String typeName) {
		return KotlinType.resolve(typeName, importResolver);
	}

	private KotlinType resolveTypeContext(
			KotlinParser.TypeContext typeCtx) {
		if (typeCtx == null) {
			return KotlinType.UNKNOWN;
		}
		// Extract the type name from the TypeContext tree. Try
		// typeReference first, then nullableType, then fallback.
		String text = typeCtx.getText();
		if (text == null || text.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		// Strip trailing '?' for nullable types
		boolean nullable = text.endsWith("?");
		if (nullable) {
			text = text.substring(0, text.length() - 1);
		}
		// Strip type arguments (e.g., "List<String>" -> "List")
		int angleIdx = text.indexOf('<');
		if (angleIdx >= 0) {
			text = text.substring(0, angleIdx);
		}
		KotlinType resolved = resolveTypeName(text);
		if (nullable) {
			resolved = resolved.withNullable(true);
		}
		return resolved;
	}

	private KotlinType resolveControlStructureBodyType(
			KotlinParser.ControlStructureBodyContext ctx) {
		if (ctx == null) {
			return KotlinType.UNKNOWN;
		}
		if (ctx.statement() != null
				&& ctx.statement().expression() != null) {
			return resolve(ctx.statement().expression());
		}
		if (ctx.block() != null) {
			return resolveBlockType(ctx.block());
		}
		return KotlinType.UNKNOWN;
	}

	private void pushLambdaScope(KotlinType receiverType,
			String functionName,
			KotlinParser.CallSuffixContext callCtx) {
		if (functionName == null) {
			return;
		}
		LambdaTypeResolver.LambdaContext lambdaCtx =
				lambdaTypeResolver.resolveLambdaContext(
						receiverType, functionName, 0);
		if (lambdaCtx == null) {
			return;
		}
		KotlinParser.LambdaLiteralContext lambdaLiteral = null;
		if (callCtx != null && callCtx.annotatedLambda() != null) {
			lambdaLiteral =
					callCtx.annotatedLambda().lambdaLiteral();
		}
		LambdaTypeResolver.pushLambdaBindings(lambdaCtx,
				scopeChain, lambdaLiteral);
	}

	private String getPrimaryName(
			KotlinParser.PostfixUnaryExpressionContext ctx) {
		if (ctx == null || ctx.primaryExpression() == null) {
			return null;
		}
		KotlinParser.SimpleIdentifierContext id =
				ctx.primaryExpression().simpleIdentifier();
		return id != null ? id.getText() : null;
	}

	private int countArguments(
			KotlinParser.CallSuffixContext callCtx) {
		if (callCtx == null) {
			return 0;
		}
		int argCount = 0;
		if (callCtx.valueArguments() != null
				&& callCtx.valueArguments().valueArgument() != null) {
			argCount = callCtx.valueArguments().valueArgument()
					.size();
		}
		if (callCtx.annotatedLambda() != null) {
			argCount++;
		}
		return argCount;
	}
}
