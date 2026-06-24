config.plugins = config.plugins || [];
config.plugins.push(new (require("node-polyfill-webpack-plugin"))());

(config.module.rules || []).forEach((rule) => {
    if (Array.isArray(rule.use) && rule.use.includes("source-map-loader")) {
        rule.exclude = /kotlin/;
    }
});

config.module.parser = config.module.parser || {};
config.module.parser.javascript = Object.assign({}, config.module.parser.javascript || {}, {
    exprContextCritical: false,
});

config.resolve = config.resolve || {};
config.resolve.fallback = Object.assign({}, config.resolve.fallback || {}, {
    os: require.resolve("os-browserify/browser"),
    path: require.resolve("path-browserify"),
});

config.optimization = config.optimization || {};
config.optimization.minimize = true;
config.optimization.minimizer = [
    new (require("terser-webpack-plugin"))({
        terserOptions: {
            mangle: true,    // Note: By default, mangle is set to true.
            compress: false, // Disable the transformations that reduce the code size.
            output: {
                beautify: false,
            },
        },
    }),
];

config.performance = config.performance || {};
config.performance.hints = false;

config.ignoreWarnings = (config.ignoreWarnings || []).concat([
    /Critical dependency: the request of a dependency is an expression/,
]);

config.stats = "errors-only";
