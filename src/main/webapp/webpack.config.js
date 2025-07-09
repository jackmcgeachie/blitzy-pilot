const path = require('path');
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const CompressionPlugin = require('compression-webpack-plugin');
const CopyWebpackPlugin = require('copy-webpack-plugin');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
const { CleanWebpackPlugin } = require('clean-webpack-plugin');

/**
 * Webpack Configuration for CardDemo React Frontend Application
 * 
 * This configuration implements comprehensive build optimization for the 17 React components
 * replacing BMS 3270 terminal screens in the CardDemo credit card management system.
 * 
 * Key Features:
 * - Multi-stage Docker build support for containerized deployment
 * - Code splitting and lazy loading for optimal performance
 * - TypeScript compilation with React 18.x and JSX transformation
 * - Static asset optimization with minification and compression
 * - Development server with hot module replacement
 * - Production build with tree-shaking and bundle optimization
 * - CSS modules and Material-UI theming integration
 * 
 * Architecture Support:
 * - React 18.2.0 with functional components and hooks
 * - TypeScript 5.x for enterprise-grade type safety
 * - Material-UI v5.14.x for consistent component library
 * - Redux with Redux Toolkit for centralized state management
 * - Support for 17 responsive web components replacing BMS screens
 */

// Environment detection for development vs production builds
const isProduction = process.env.NODE_ENV === 'production';
const isDevelopment = !isProduction;
const shouldAnalyze = process.env.ANALYZE === 'true';

/**
 * Main webpack configuration object
 * Exports configuration supporting both development and production builds
 * with environment-specific optimizations and plugins
 */
const webpackConfig = {
  // Build mode configuration
  mode: isProduction ? 'production' : 'development',
  
  // Development tool configuration for source maps
  devtool: isProduction ? 'source-map' : 'eval-cheap-module-source-map',
  
  // Entry point configuration with code splitting
  entry: {
    // Main application entry point
    main: './src/index.tsx',
    
    // Vendor chunk for third-party libraries
    vendor: [
      'react',
      'react-dom',
      '@reduxjs/toolkit',
      'react-redux',
      '@mui/material',
      'axios',
      'react-router-dom',
      'formik',
      'yup'
    ]
  },
  
  // Output configuration for build artifacts
  output: {
    // Build output directory
    path: path.resolve(__dirname, 'build'),
    
    // Filename patterns with content hashing for caching
    filename: isProduction 
      ? 'static/js/[name].[contenthash:8].js'
      : 'static/js/[name].js',
    
    // Chunk filename for code splitting
    chunkFilename: isProduction
      ? 'static/js/[name].[contenthash:8].chunk.js'
      : 'static/js/[name].chunk.js',
    
    // Asset filename for static assets
    assetModuleFilename: 'static/media/[name].[hash][ext]',
    
    // Public path for assets (configurable for different deployment environments)
    publicPath: process.env.PUBLIC_URL || '/',
    
    // Clean output directory before each build
    clean: true,
    
    // Cross-origin loading for enhanced security
    crossOriginLoading: 'anonymous'
  },
  
  // Module resolution configuration
  resolve: {
    // File extensions to resolve
    extensions: ['.tsx', '.ts', '.jsx', '.js', '.json'],
    
    // Module resolution directories
    modules: ['node_modules', path.resolve(__dirname, 'src')],
    
    // Path aliases for clean imports matching tsconfig.json
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@/components': path.resolve(__dirname, 'src/components'),
      '@/pages': path.resolve(__dirname, 'src/pages'),
      '@/hooks': path.resolve(__dirname, 'src/hooks'),
      '@/utils': path.resolve(__dirname, 'src/utils'),
      '@/types': path.resolve(__dirname, 'src/types'),
      '@/store': path.resolve(__dirname, 'src/store'),
      '@/services': path.resolve(__dirname, 'src/services'),
      '@/constants': path.resolve(__dirname, 'src/constants'),
      '@/styles': path.resolve(__dirname, 'src/styles'),
      '@/assets': path.resolve(__dirname, 'src/assets'),
      '@/config': path.resolve(__dirname, 'src/config'),
      '@/schemas': path.resolve(__dirname, 'src/schemas'),
      
      // BMS screen replacement component paths
      '@/screens/auth': path.resolve(__dirname, 'src/components/screens/auth'),
      '@/screens/menu': path.resolve(__dirname, 'src/components/screens/menu'),
      '@/screens/account': path.resolve(__dirname, 'src/components/screens/account'),
      '@/screens/card': path.resolve(__dirname, 'src/components/screens/card'),
      '@/screens/transaction': path.resolve(__dirname, 'src/components/screens/transaction'),
      '@/screens/payment': path.resolve(__dirname, 'src/components/screens/payment'),
      '@/screens/user': path.resolve(__dirname, 'src/components/screens/user'),
      '@/screens/report': path.resolve(__dirname, 'src/components/screens/report'),
      
      // API integration paths
      '@/api': path.resolve(__dirname, 'src/services/api'),
      
      // Validation schema paths
      '@/validation': path.resolve(__dirname, 'src/schemas/validation')
    },
    
    // Fallback for Node.js modules in browser environment
    fallback: {
      "path": require.resolve("path-browserify"),
      "os": require.resolve("os-browserify/browser"),
      "crypto": require.resolve("crypto-browserify"),
      "stream": require.resolve("stream-browserify"),
      "buffer": require.resolve("buffer/")
    }
  },
  
  // Module rules for different file types
  module: {
    rules: [
      // TypeScript and JavaScript files
      {
        test: /\.(tsx?|jsx?)$/,
        exclude: /node_modules/,
        use: [
          {
            loader: 'babel-loader',
            options: {
              presets: [
                ['@babel/preset-env', {
                  targets: {
                    browsers: ['last 2 versions', 'not dead', '> 0.2%']
                  },
                  modules: false,
                  useBuiltIns: 'usage',
                  corejs: 3
                }],
                ['@babel/preset-react', {
                  runtime: 'automatic'
                }],
                '@babel/preset-typescript'
              ],
              plugins: [
                // Material-UI optimization plugin
                ['@babel/plugin-transform-react-jsx', {
                  runtime: 'automatic'
                }],
                // React Fast Refresh for development
                isDevelopment && 'react-refresh/babel'
              ].filter(Boolean),
              cacheDirectory: true,
              cacheCompression: false,
              compact: isProduction
            }
          },
          {
            loader: 'ts-loader',
            options: {
              transpileOnly: true,
              configFile: path.resolve(__dirname, 'tsconfig.json'),
              compilerOptions: {
                noEmit: false,
                module: 'esnext',
                target: 'es2018'
              }
            }
          }
        ]
      },
      
      // CSS and SCSS files
      {
        test: /\.css$/,
        use: [
          // Extract CSS in production, inject in development
          isProduction ? MiniCssExtractPlugin.loader : 'style-loader',
          {
            loader: 'css-loader',
            options: {
              modules: {
                auto: true,
                localIdentName: isProduction 
                  ? '[hash:base64:5]' 
                  : '[path][name]__[local]--[hash:base64:5]'
              },
              sourceMap: isDevelopment,
              importLoaders: 1
            }
          },
          // PostCSS for autoprefixing and optimization
          {
            loader: 'postcss-loader',
            options: {
              postcssOptions: {
                plugins: [
                  'autoprefixer',
                  isProduction && 'cssnano'
                ].filter(Boolean)
              }
            }
          }
        ]
      },
      
      // SCSS files with Material-UI theme support
      {
        test: /\.s[ac]ss$/,
        use: [
          isProduction ? MiniCssExtractPlugin.loader : 'style-loader',
          {
            loader: 'css-loader',
            options: {
              modules: {
                auto: true,
                localIdentName: isProduction 
                  ? '[hash:base64:5]' 
                  : '[path][name]__[local]--[hash:base64:5]'
              },
              sourceMap: isDevelopment,
              importLoaders: 2
            }
          },
          {
            loader: 'postcss-loader',
            options: {
              postcssOptions: {
                plugins: [
                  'autoprefixer',
                  isProduction && 'cssnano'
                ].filter(Boolean)
              }
            }
          },
          {
            loader: 'sass-loader',
            options: {
              sourceMap: isDevelopment,
              sassOptions: {
                includePaths: [path.resolve(__dirname, 'src/styles')]
              }
            }
          }
        ]
      },
      
      // Image files
      {
        test: /\.(png|jpe?g|gif|svg)$/i,
        type: 'asset',
        parser: {
          dataUrlCondition: {
            maxSize: 8 * 1024 // 8KB
          }
        },
        generator: {
          filename: 'static/media/[name].[hash:8][ext]'
        }
      },
      
      // Font files
      {
        test: /\.(woff|woff2|eot|ttf|otf)$/i,
        type: 'asset/resource',
        generator: {
          filename: 'static/fonts/[name].[hash:8][ext]'
        }
      },
      
      // JSON files
      {
        test: /\.json$/,
        type: 'json'
      }
    ]
  },
  
  // Plugin configuration
  plugins: [
    // Clean build directory
    new CleanWebpackPlugin({
      cleanStaleWebpackAssets: false,
      cleanOnceBeforeBuildPatterns: ['**/*', '!.gitkeep']
    }),
    
    // HTML template generation
    new HtmlWebpackPlugin({
      template: path.resolve(__dirname, 'public/index.html'),
      filename: 'index.html',
      inject: 'body',
      minify: isProduction ? {
        removeComments: true,
        collapseWhitespace: true,
        removeRedundantAttributes: true,
        useShortDoctype: true,
        removeEmptyAttributes: true,
        removeStyleLinkTypeAttributes: true,
        keepClosingSlash: true,
        minifyJS: true,
        minifyCSS: true,
        minifyURLs: true
      } : false,
      meta: {
        viewport: 'width=device-width, initial-scale=1, shrink-to-fit=no',
        'theme-color': '#1976d2',
        description: 'CardDemo - Modern Credit Card Management System'
      }
    }),
    
    // Environment variables
    new webpack.DefinePlugin({
      'process.env.NODE_ENV': JSON.stringify(process.env.NODE_ENV),
      'process.env.REACT_APP_VERSION': JSON.stringify(process.env.npm_package_version),
      'process.env.REACT_APP_API_BASE_URL': JSON.stringify(process.env.REACT_APP_API_BASE_URL || '/api/v1'),
      'process.env.REACT_APP_WS_BASE_URL': JSON.stringify(process.env.REACT_APP_WS_BASE_URL || 'ws://localhost:8080/ws')
    }),
    
    // Production-specific plugins
    ...(isProduction ? [
      // Extract CSS into separate files
      new MiniCssExtractPlugin({
        filename: 'static/css/[name].[contenthash:8].css',
        chunkFilename: 'static/css/[name].[contenthash:8].chunk.css',
        ignoreOrder: false
      }),
      
      // Gzip compression
      new CompressionPlugin({
        filename: '[path][base].gz',
        algorithm: 'gzip',
        test: /\.(js|css|html|svg)$/,
        threshold: 8192,
        minRatio: 0.8
      }),
      
      // Brotli compression
      new CompressionPlugin({
        filename: '[path][base].br',
        algorithm: 'brotliCompress',
        test: /\.(js|css|html|svg)$/,
        threshold: 8192,
        minRatio: 0.8
      }),
      
      // Copy static assets
      new CopyWebpackPlugin({
        patterns: [
          {
            from: path.resolve(__dirname, 'public'),
            to: path.resolve(__dirname, 'build'),
            globOptions: {
              ignore: ['**/index.html']
            }
          }
        ]
      })
    ] : []),
    
    // Development-specific plugins
    ...(isDevelopment ? [
      // Hot module replacement
      new webpack.HotModuleReplacementPlugin(),
      
      // Better error messages
      new webpack.NoEmitOnErrorsPlugin()
    ] : []),
    
    // Bundle analyzer (only when ANALYZE=true)
    ...(shouldAnalyze ? [
      new BundleAnalyzerPlugin({
        analyzerMode: 'static',
        openAnalyzer: true,
        reportFilename: 'bundle-report.html'
      })
    ] : [])
  ],
  
  // Optimization configuration
  optimization: {
    minimize: isProduction,
    minimizer: [
      // JavaScript minification
      new TerserPlugin({
        terserOptions: {
          parse: {
            ecma: 8
          },
          compress: {
            ecma: 5,
            warnings: false,
            comparisons: false,
            inline: 2,
            drop_console: isProduction,
            drop_debugger: isProduction
          },
          mangle: {
            safari10: true
          },
          output: {
            ecma: 5,
            comments: false,
            ascii_only: true
          }
        },
        parallel: true,
        extractComments: false
      })
    ],
    
    // Code splitting configuration
    splitChunks: {
      chunks: 'all',
      cacheGroups: {
        // Vendor libraries
        vendor: {
          test: /[\\/]node_modules[\\/]/,
          name: 'vendors',
          priority: 10,
          chunks: 'all'
        },
        
        // React-specific libraries
        react: {
          test: /[\\/]node_modules[\\/](react|react-dom)[\\/]/,
          name: 'react',
          priority: 20,
          chunks: 'all'
        },
        
        // Material-UI libraries
        mui: {
          test: /[\\/]node_modules[\\/]@mui[\\/]/,
          name: 'mui',
          priority: 15,
          chunks: 'all'
        },
        
        // Common chunks
        common: {
          name: 'common',
          minChunks: 2,
          priority: 5,
          chunks: 'all',
          reuseExistingChunk: true
        }
      }
    },
    
    // Runtime chunk configuration
    runtimeChunk: {
      name: 'runtime'
    },
    
    // Module concatenation for better tree-shaking
    concatenateModules: isProduction,
    
    // Ensure deterministic module ids
    moduleIds: isProduction ? 'deterministic' : 'named',
    chunkIds: isProduction ? 'deterministic' : 'named'
  },
  
  // Performance configuration
  performance: {
    hints: isProduction ? 'warning' : false,
    maxEntrypointSize: 512000,
    maxAssetSize: 512000,
    assetFilter: (assetFilename) => {
      return assetFilename.endsWith('.js') || assetFilename.endsWith('.css');
    }
  },
  
  // Development server configuration
  devServer: {
    // Server configuration
    host: '0.0.0.0',
    port: process.env.PORT || 3000,
    
    // Content serving
    static: {
      directory: path.resolve(__dirname, 'public'),
      publicPath: '/',
      watch: true
    },
    
    // Hot module replacement
    hot: true,
    liveReload: true,
    
    // History API fallback for client-side routing
    historyApiFallback: {
      disableDotRule: true,
      index: '/index.html'
    },
    
    // Proxy configuration for API calls
    proxy: {
      '/api': {
        target: process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080',
        changeOrigin: true,
        secure: false,
        logLevel: 'debug'
      },
      '/ws': {
        target: process.env.REACT_APP_WS_BASE_URL || 'ws://localhost:8080',
        ws: true,
        changeOrigin: true
      }
    },
    
    // Open browser on start
    open: isDevelopment,
    
    // Overlay for errors
    client: {
      overlay: {
        errors: true,
        warnings: false
      },
      progress: true
    },
    
    // Headers for security
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, PATCH, OPTIONS',
      'Access-Control-Allow-Headers': 'X-Requested-With, content-type, Authorization'
    },
    
    // Compression
    compress: true,
    
    // Enable HTTPS in development if needed
    https: process.env.HTTPS === 'true'
  },
  
  // External dependencies (for CDN usage if needed)
  externals: {},
  
  // Resolve loader configuration
  resolveLoader: {
    modules: ['node_modules']
  },
  
  // Stats configuration for build output
  stats: {
    colors: true,
    hash: false,
    version: false,
    timings: true,
    assets: false,
    chunks: false,
    modules: false,
    reasons: false,
    children: false,
    source: false,
    errors: true,
    errorDetails: true,
    warnings: true,
    publicPath: false
  }
};

module.exports = webpackConfig;