const path = require('path')
// const fs = require('fs')
const MiniCssExtractPlugin = require('mini-css-extract-plugin')
const CssMinimizerPlugin = require('css-minimizer-webpack-plugin')
const { VueLoaderPlugin } = require('vue-loader')
const CopyWebpackPlugin = require('copy-webpack-plugin')
const BundleWebpackPlugin = require('./webpackPlugin/bundle-webpack-plugin')
// const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin

module.exports = ({ entry, publicPath, dist, port = 8080, argv, env }) => {
    const isDev = argv.mode === 'development'
    const envDist = env && env.dist ? env.dist : 'frontend'
    const buildDist = path.join(__dirname, envDist, dist)
    return {
        cache: {
            type: 'filesystem',
            buildDependencies: {
                config: [__filename]
            }
        },
        devtool: 'eval-cheap-module-source-map',
        entry,
        output: {
            publicPath,
            chunkFilename: '[name].[chunkhash].js',
            filename: '[name].[contenthash].min.js',
            path: buildDist,
            assetModuleFilename: '[name].[ext]?[contenthash]'
        },
        module: {
            rules: [
                {
                    test: /\.vue$/,
                    include: [path.resolve('src'), path.resolve('../node_modules/vue-echarts'), path.resolve(__dirname, './common-lib')],
                    loader: 'vue-loader'
                },
                {
                    test: /\.js$/,
                    include: [path.resolve('src'), path.resolve('../node_modules/vue-echarts'), path.resolve(__dirname, './common-lib'), path.resolve(__dirname, './locale')],
                    use: [
                        { loader: 'babel-loader' }
                    ]
                },
                {
                    test: /\.css$/,
                    use: [{
                        loader: MiniCssExtractPlugin.loader,
                        options: {
                            publicPath: (resourcePath, context) => ''
                        }
                    }, 'css-loader']
                },
                {
                    test: /\.scss$/,
                    use: [isDev
                        ? 'style-loader'
                        : {
                            loader: MiniCssExtractPlugin.loader,
                            options: {
                                publicPath: (resourcePath, context) => ''
                            }
                        }, 'css-loader', 'sass-loader']
                },
                {
                    test: /\.(js|vue)$/,
                    loader: 'eslint-loader',
                    enforce: 'pre',
                    include: [path.resolve('src'), path.resolve(__dirname, './common-lib'), path.resolve(__dirname, './locale')],
                    exclude: /node_modules/,
                    options: {
                        fix: true,
                        emitWarning: false,
                        formatter: require('eslint-friendly-formatter')
                    }
                },
                {
                    test: /\.cur$/,
                    type: 'asset/resource'
                },
                {
                    test: /\.(png|jpe?g|gif|svg|webp|woff2?|eot|ttf|otf)(\?.*)?$/,
                    type: 'asset',
                    parser: {
                        dataUrlCondition: {
                            maxSize: 8 * 1024
                        }
                    },
                    generator: {
                        // publicPath: 'auto',
                        filename: '[name].[contenthash].[ext]'
                    }
                }
            ]
        },
        plugins: [
            // new BundleAnalyzerPlugin(),
            new VueLoaderPlugin(),
            new BundleWebpackPlugin({
                dist: envDist,
                isDev,
                entryFolderName: "entry's"
            }),
            new MiniCssExtractPlugin({
                filename: '[name].[contenthash].css',
                chunkFilename: '[id].[contenthash].css',
                ignoreOrder: true
            }),
            new CopyWebpackPlugin({
                patterns: [
                    {
                        from: path.join(__dirname, 'locale', dist),
                        to: buildDist
                    }],
                options: {
                    concurrency: 100
                }
            })
        ],
        optimization: {
            chunkIds: isDev ? 'named' : 'deterministic',
            moduleIds: 'deterministic',
            minimize: !isDev,
            splitChunks: {
                cacheGroups: {
                    bkMagicVue: {
                        test: module => {
                            return /bk-magic-vue/.test(module.context)
                        },
                        name: 'bk-magic-vue-chunk', // chunk 的名字
                        chunks: 'all', // 可能的值 'async', 'initial', 'all',
                        reuseExistingChunk: true
                    },
                    default: {
                        minChunks: 2,
                        priority: -20,
                        reuseExistingChunk: true
                    }

                }
            },
              
            minimizer: [
                new CssMinimizerPlugin({
                    minimizerOptions: {
                        preset: [
                            'default',
                            {
                                discardComments: { removeAll: true },
                                discardDuplicates: true,
                                normalizeCharset: true
                            }
                        ]
                    }
                })
            ]
        },
        resolve: {
            extensions: ['.js', '.vue', '.json', '.ts', '.scss', '.css'],
            fallback: {
                path: false
            },
            alias: {
                '@': path.resolve('src'),
                '@locale': path.resolve(__dirname, 'locale')
            }
        },
        externals: {
            vue: 'Vue',
            'vue-router': 'VueRouter',
            vuex: 'Vuex'
        },
        devServer: {
            static: {
                directory: path.join(__dirname, envDist),
                watch: false
            },
            allowedHosts: 'all',
            historyApiFallback: true,
            client: {
                webSocketURL: 'ws://127.0.0.1:' + port + '/ws'
            },
            // https: {
            //     key: fs.readFileSync(path.join(__dirname, 'localhost+2-key.pem')),
            //     cert: fs.readFileSync(path.join(__dirname, './localhost+2.pem'))
            // },
            hot: isDev,
            port
        }
    }
}
