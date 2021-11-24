package il.ac.technion.cs.mipphd.graal.graphquery;
class B implements Comparable<B> {
    int field0, field1, field2;

    public int compareTo(B parameter0) {
        return this.field2 - parameter0.field2;
    }
};
interface D {
     void f(B[] field2);
}

class C {
    int field0, field1;
};

interface F {
    int f0(C[] c,int i);
    int f1(C[] c,int i1, int i2);
}
class AlgorithmSample {
    int field0, field1;
    F f;
    D d;
    B field2[];

    AlgorithmSample(int parameter0, int parameter1) {
        field0 = parameter0;
        field1 = parameter1;
        field2 = new B[field1];
        for (int i = 0; i < parameter1; ++i)
            field2[i] = new B();
    }

    public void f2() {
        B v0[] = new B[field0];
        int v2 = 0;

        d.f(field2);
        C v3[] = new C[field0];
        for (v2 = 0; v2 < field0; ++v2){
            v3[v2] = new C();
            v3[v2].field0 = v2;
            v3[v2].field1 = 0;
        }
        int v1 = 0;
        v2 = 0;
        while (v1 < field0 - 1) {
            B v4 = field2[v2++];
            int v5 = f.f0(v3, v4.field0);
            int v6 = f.f0(v3, v4.field1);
            if (v5 != v6) {
                v0[v1++] = v4;
                f.f1(v3, v5, v6);
            }
        }
//    for (v2 = 0; v2 < v1; ++v2)
//      System.out.println(v0[v2].field0 + " - " + v0[v2].field1 + ": " + v0[v2].field2);
    }

}