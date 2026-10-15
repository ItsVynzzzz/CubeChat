@file:Suppress("PackageDirectoryMismatch")

package javax.naming.directory

import java.util.Hashtable

/*
 * Android tidak punya javax.naming (JNDI). MCProtocolLib (NettyHelper) memakainya di static
 * initializer untuk lookup SRV, sehingga tanpa stub ini app langsung crash
 * (ExceptionInInitializerError) begitu tombol Connect ditekan.
 * Stub ini membuat lookup SRV selalu "tidak ada record" -> MCPL memakai resolusi DNS biasa.
 * Tanda tangan method sudah dicocokkan dengan bytecode MCPL.
 */
interface Attribute {
    fun get(): Any?
}

interface Attributes {
    fun get(attrID: String): Attribute?
}

interface DirContext {
    fun getAttributes(name: String, attrIds: Array<String>): Attributes
}

class InitialDirContext(@Suppress("UNUSED_PARAMETER") environment: Hashtable<*, *>) : DirContext {
    override fun getAttributes(name: String, attrIds: Array<String>): Attributes =
        object : Attributes {
            override fun get(attrID: String): Attribute? = null
        }
}
