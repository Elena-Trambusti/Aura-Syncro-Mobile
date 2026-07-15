// Se @/ non funziona, aggiungi in vite.config.ts:
import path from 'path';

export default {
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
};

// E in tsconfig.json / tsconfig.app.json:
// "compilerOptions": {
//   "baseUrl": ".",
//   "paths": { "@/*": ["src/*"] }
// }
