package il.ac.technion.cs.mipphd.graal.graphquery;

public class A {
    int num;
    int id;
    public A(int num, int id) {
        this.num = num;
        this.id = id;
    }
    public A(int num) {
        this.num = num;
        this.id = 0;
//        jdk.vm.ci.meta.ResolvedJavaType type = null;
//        jdk.vm.ci.meta.ResolvedJavaField field = null;
    }
    private static int x, y, z;
    public static void accessingFunction(A a, A b, A c) {
        x = a.num;
        y = b.num;
        z = c.num;
//        PointsToAnalysisKt.getForceAllocSet().add(x);
//        PointsToAnalysisKt.getForceAllocSet().add(y);
//        PointsToAnalysisKt.getForceAllocSet().add(z);
    }
}
