package il.ac.technion.cs.mipphd.graal.graphquery

class Listable(val l: List<Int>) {
    fun maximum(): Int {
        var i = 0
        var max = -1
        while (i < l.size) {
            val elem = l[i]
            if (elem >= max)
                max = elem
            ++i
        }
        return max
    }
}