@file:Suppress("NOTHING_TO_INLINE")

package com.sxtanna.util

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.safeCast

sealed class Result<T : Any>

data class Some<T : Any>(val data: T)
	: Result<T>()
{
	override fun toString(): String
	{
		return "Some[$data]"
	}
}

data class None<T : Any>(val info: Throwable)
	: Result<T>()
{
	override fun toString(): String
	{
		return "None[${info::class.simpleName}: ${info.message}]"
	}
}


inline fun <T : Any> of(data: T? = null): Result<T>
{
	return try
	{
		Some(requireNotNull(data))
	}
	catch (ex: Throwable)
	{
		None(ex)
	}
}

inline fun <T : Any> of(func: () -> T?): Result<T>
{
	contract {
		callsInPlace(func, InvocationKind.EXACTLY_ONCE)
	}
	
	return try
	{
		of(func())
	}
	catch (ex: Throwable)
	{
		None(ex)
	}
}


inline fun <T : Any> Result<T>.isSome(): Boolean
{
	contract {
		returns(true) implies (this@isSome is Some<T>)
	}
	
	return this is Some<T>
}

inline fun <T : Any> Result<T>.isNone(): Boolean
{
	contract {
		returns(true) implies (this@isNone is None<T>)
	}
	
	return this is None<T>
}


inline fun <T : Any> Result<T>.ifSome(func: (data: T) -> Unit): Result<T>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	if (this is Some)
	{
		func(this.data)
	}
	
	return this
}

inline fun <T : Any> Result<T>.ifNone(func: (info: Throwable) -> Unit): Result<T>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	if (this is None)
	{
		func(this.info)
	}
	
	return this
}


inline fun <T : Any> Result<T>.ifSomeThrow(func: (data: T) -> Throwable): None<T>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> this
		is Some ->
		{
			throw func(this.data)
		}
	}
}

inline fun <T : Any> Result<T>.ifNoneThrow(func: (info: Throwable) -> Throwable = { it }): Some<T>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is Some -> this
		is None ->
		{
			throw func(this.info)
		}
	}
}


inline fun <T : Any> Result<T>.orElse(data: T): T
{
	return when (this)
	{
		is None -> data
		is Some -> this.data
	}
}

inline fun <T : Any> Result<T>.orElse(func: () -> T): T
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> func()
		is Some -> this.data
	}
}

inline fun <I : Any, reified O : Any> Result<I>.castTo(): Result<O>
{
	return map { it as? O }
}


inline fun <T : Any> Result<T>.handle(whenIsSome: (data: T) -> Unit, whenIsNone: (info: Throwable) -> Unit): Result<T>
{
	contract {
		callsInPlace(whenIsSome, InvocationKind.AT_MOST_ONCE)
		callsInPlace(whenIsNone, InvocationKind.AT_MOST_ONCE)
	}
	
	when (this)
	{
		is Some -> whenIsSome(this.data)
		is None -> whenIsNone(this.info)
	}
	
	return this
}


inline fun <T : Any> Result<T>.req(condition: (T) -> Boolean, message: String): Result<T>
{
	contract {
		callsInPlace(condition, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(this.info)
		is Some -> of()
		{
			require(condition(this.data))
			{
				message
			}
			
			this.data
		}
	}
}


inline fun <T : Any> Result<*>.and(data: T? = null): Result<T>
{
	return when (this)
	{
		is None -> None(this.info)
		is Some -> of(data)
	}
}

inline fun <T : Any> Result<*>.and(func: () -> T?): Result<T>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(this.info)
		is Some -> of(func)
	}
}


inline fun <I : Any, O : Any> Result<I>.map(func: (data: I) -> O?): Result<O>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(info)
		is Some -> of()
		{
			func.invoke(data)
		}
	}
}

inline fun <I : Any, O : Any> Result<I>.let(func: (data: I) -> Result<O>): Result<O>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(info)
		is Some -> of()
		{
			when (val result = func.invoke(data))
			{
				is Some -> result.data
				is None -> throw result.info
			}
		}
	}
}

inline fun <I : Any, O : Any> Result<I>.mapUse(func: (data: Result<I>) -> O?): Result<O>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(info)
		is Some -> of()
		{
			func.invoke(this)
		}
	}
}

inline fun <I : Any, O : Any> Result<I>.letUse(func: (data: Result<I>) -> Result<O>): Result<O>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(info)
		is Some -> of()
		{
			when (val result = func.invoke(this))
			{
				is Some -> result.data
				is None -> throw result.info
			}
		}
	}
}


inline fun <I : Any, O : Any> Result<out Collection<I>>.mapEach(func: (data: I) -> O?): Result<List<Result<O>>>
{
	contract {
		callsInPlace(func, InvocationKind.UNKNOWN)
	}
	
	return when (this)
	{
		is None -> None(this.info)
		is Some -> of()
		{
			this.data.map()
			{ d ->
				of { func(d) }
			}
		}
	}
}

inline fun <I : Any, O : Any> Result<out Collection<I>>.mapFold(init: O, func: (targ: O, data: I) -> O): Result<out O>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(this.info)
		is Some -> of()
		{
			this.data.fold(init, func)
		}
	}
}


inline fun <I : Any, O : Any, W : Any?> Result<I>.mapWith(with: W, func: (data: I, with: W) -> O?): Result<O>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(info)
		is Some -> of()
		{
			func.invoke(data, with)
		}
	}
}

inline fun <I : Any, O : Any, W0 : Any, W1 : Any> Result<I>.mapWith(with: Pair<W0, W1>, func: (data: I, with0: W0, with1: W1) -> O?): Result<O>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(info)
		is Some -> of()
		{
			val (with0, with1) = with
			
			func.invoke(data, with0, with1)
		}
	}
}

inline fun <I : Any, O : Any, W0 : Any, W1 : Any, W2 : Any> Result<I>.mapWith(with: Triple<W0, W1, W2>, func: (data: I, with0: W0, with1: W1, with2: W2) -> O?): Result<O>
{
	contract {
		callsInPlace(func, InvocationKind.AT_MOST_ONCE)
	}
	
	return when (this)
	{
		is None -> None(info)
		is Some -> of()
		{
			val (with0, with1, with2) = with
			
			func.invoke(data, with0, with1, with2)
		}
	}
}