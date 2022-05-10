package il.ac.technion.cs.mipphd.graal.utils;

import il.ac.technion.cs.mipphd.graal.utils.CFGWrapper;
import il.ac.technion.cs.mipphd.graal.utils.GraalAdapter;
import il.ac.technion.cs.mipphd.graal.utils.MethodWrapper;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCICompiler;
import jdk.vm.ci.services.Services;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.phases.CommunityCompilerConfiguration;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.runtime.RuntimeProvider;

import java.lang.reflect.Method;


public class MethodToGraph {
    private final GraalRuntime runtime = initializeRuntime();
    private final OptionValues options = runtime.getCapability(OptionValues.class);
    private final DebugContext context = (new DebugContext.Builder(options)).build();
    private final StructuredGraph.Builder graphBuilder = new StructuredGraph.Builder(options, context);
    private final RuntimeProvider runtimeProvider = runtime.getCapability(RuntimeProvider.class);
    private final Backend backend = runtimeProvider.getHostBackend();
    private final MetaAccessProvider metaAccess = backend.getMetaAccess();

    protected StructuredGraph getEmptyGraph(ResolvedJavaMethod method) {
        return graphBuilder
                .method(method)
                .compilationId(backend.getCompilationIdentifier(method))
                .build();
    }

    protected StructuredGraph getGraph(ResolvedJavaMethod method) {
        StructuredGraph graph = getEmptyGraph(method);
        GraphBuilderConfiguration.Plugins gbcPlugins = new GraphBuilderConfiguration.Plugins(new InvocationPlugins());
        GraphBuilderConfiguration graphBuilderConfiguration = GraphBuilderConfiguration.getDefault(gbcPlugins)
                .withEagerResolving(true)
                .withUnresolvedIsError(true)
                .withNodeSourcePosition(true);
        GraphBuilderPhase.Instance graphBuilder = new GraphBuilderPhase.Instance(backend.getProviders(), graphBuilderConfiguration, OptimisticOptimizations.NONE, null);
        graphBuilder.apply(graph);
        graph.maybeCompress();

        PhaseSuite<HighTierContext> graphBuilderSuite = backend.getSuites().getDefaultGraphBuilderSuite();
        CommunityCompilerConfiguration config = new CommunityCompilerConfiguration();
        HighTierContext highTierContext = new HighTierContext(backend.getProviders(), graphBuilderSuite, OptimisticOptimizations.NONE);

        PhaseSuite<HighTierContext> tier = config.createHighTier(options);
        tier.removePhase(LoweringPhase.class);
        tier.apply(graph, highTierContext);
        graph.maybeCompress();
        return graph;
    }

    public MethodWrapper lookupJavaMethodToWrapper(Method method) {
        return new MethodWrapper(metaAccess.lookupJavaMethod(method));
    }

    private ResolvedJavaMethod lookupJavaMethod(Method method) {
        return metaAccess.lookupJavaMethod(method);
    }

    public CFGWrapper getCFG(Method method) {
        return getCFG(lookupJavaMethod(method));
    }

    private CFGWrapper getCFG(ResolvedJavaMethod method) {
        return new CFGWrapper(ControlFlowGraph.compute(getGraph(method), true, true, true, true));
    }

    public CFGWrapper getCFGFromWrapper(MethodWrapper method) {
        return getCFG(method.getResolvedJavaMethod());
    }

    public void printCFG(ControlFlowGraph cfg) {
        for (Block block : cfg.getBlocks()) {
            System.out.println();
            System.out.println(block.toString(Verbosity.All));
        }
    }

    public GraalAdapter getAdaptedCFG(Method method) {
        return GraalAdapter.fromGraal(getCFG(method));
    }

    static private GraalRuntime initializeRuntime() {
        Services.initializeJVMCI();
        JVMCICompiler compiler = JVMCI.getRuntime().getCompiler();
        if (compiler instanceof GraalJVMCICompiler)
            return ((GraalJVMCICompiler) compiler).getGraalRuntime();
        return new InvalidGraalRuntime();
    }

    private static class InvalidGraalRuntime implements GraalRuntime {
        @Override
        public String getName() {
            return null;
        }

        @Override
        public <T> T getCapability(Class<T> clazz) {
            return null;
        }
    }
}
