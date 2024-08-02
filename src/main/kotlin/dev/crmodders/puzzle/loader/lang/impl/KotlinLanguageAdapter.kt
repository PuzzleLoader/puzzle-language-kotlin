package dev.crmodders.puzzle.loader.lang.impl

import dev.crmodders.puzzle.loader.lang.LanguageAdapter
import dev.crmodders.puzzle.loader.lang.LanguageAdapterException
import dev.crmodders.puzzle.loader.launch.Piece
import dev.crmodders.puzzle.loader.mod.info.ModInfo
import dev.crmodders.puzzle.util.ClassUtil
import java.lang.invoke.MethodHandleProxies
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.full.createInstance

class KotlinLanguageAdapter : LanguageAdapter {

    override fun <T> create(info: ModInfo, value: String, type: Class<T>): T {
        val split = value.split("::");

        if (split.size >= 3) throw LanguageAdapterException("Invalid format for handle: $value")

        val clazz = try {
            Class.forName(split[0], false, Piece.classLoader) as Class<*>
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
        }

        val kClass = clazz.kotlin

        if (split.size == 1) {
            return if (type.isAssignableFrom(type)) {
                kClass.objectInstance as? T
                    ?: try {
                        kClass.createInstance() as T
                    } catch (e: Exception) {
                        throw LanguageAdapterException(e)
                    }
            } else throw LanguageAdapterException("Class " + clazz.simpleName + " is not able to be cast to ${type.name}!")
        }
        val instance = kClass.objectInstance ?: run {
            return LanguageAdapter.getDefault().create(info, value, type)
        }

        val name = split[1]
        val methodList = clazz.methods.filter { m -> name == m.name }

        clazz.declaredFields.find {
            it.name == name
        }?.let { field ->
            try {
                val fType = field.type

                if (methodList.isNotEmpty()) {
                    throw LanguageAdapterException("Ambiguous $value - refers to both field and method!")
                }

                if (!type.isAssignableFrom(fType)) {
                    throw LanguageAdapterException("Field " + value + " cannot be cast to " + type.name + "!")
                }

                return field.get(instance) as T
            } catch (e: NoSuchFieldException) {
                // ignore
            } catch (e: IllegalAccessException) {
                throw LanguageAdapterException("Field $value cannot be accessed!", e)
            }
        }

        if (!type.isInterface) {
            throw LanguageAdapterException("Cannot proxy method " + value + " to non-interface type " + type.name + "!")
        }

        if (methodList.isEmpty()) {
            throw LanguageAdapterException("Could not find $value!")
        } else if (methodList.size >= 2) {
            throw LanguageAdapterException("Found multiple method entries of name $value!")
        }

        val handle = try {
            MethodHandles.lookup()
                .unreflect(methodList[0])
                .bindTo(instance)
        } catch (ex: Exception) {
            throw LanguageAdapterException("Failed to create method handle for $value!", ex)
        }

        try {
            return MethodHandleProxies.asInterfaceInstance(type, handle)
        } catch (ex: Exception) {
            throw LanguageAdapterException(ex)
        }
    }

}